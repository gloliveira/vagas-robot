package br.com.vagas.scraper;

import br.com.vagas.model.ResultadoBusca;
import br.com.vagas.model.Vaga;
import br.com.vagas.util.FalhaHttpException;
import br.com.vagas.util.HttpUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Scraper para o InfoJobs Brasil.
 * URL atual: /vagas-de-emprego-{termo}.aspx
 * Cards: div.js_cardLink[data-href] com título em .js_vacancyTitle
 */
public class InfoJobsScraper extends ScraperBase {

    private static final Logger logger = LoggerFactory.getLogger(InfoJobsScraper.class);
    private static final String BASE_URL = "https://www.infojobs.com.br";
    private static final String REFERER = "https://www.infojobs.com.br/";

    @Override
    public String getNomePlataforma() {
        return "InfoJobs";
    }

    @Override
    public ResultadoBusca buscarVagas() {
        List<Vaga> todasVagas = new ArrayList<>();
        falhaHttp = null;
        logger.info("Iniciando busca no InfoJobs Brasil...");

        List<String> termos = Arrays.asList(
                "desenvolvedor+java",
                "java+spring+boot",
                "desenvolvedor+java+pleno",
                "desenvolvedor+java+senior"
        );

        for (String termo : termos) {
            logger.info("InfoJobs - Buscando home office: '{}'", termo.replace('+', ' '));
            List<Vaga> vagasRemoto = buscarComParametros(termo, true);
            todasVagas.addAll(vagasRemoto);
            logger.info("InfoJobs - Encontradas {} vagas home office para '{}'",
                    vagasRemoto.size(), termo.replace('+', ' '));
        }

        logger.info("InfoJobs - Total de vagas encontradas: {}", todasVagas.size());
        return fecharResultado(todasVagas);
    }

    private List<Vaga> buscarComParametros(String termo, boolean homeOffice) {
        List<Vaga> vagas = new ArrayList<>();
        int maxPaginas = 3;

        for (int pagina = 1; pagina <= maxPaginas; pagina++) {
            try {
                String url = construirUrl(termo, homeOffice, pagina);
                logger.debug("InfoJobs URL: {}", url);

                Document doc = HttpUtil.get(url, REFERER);
                if (documentoBloqueado(doc)) {
                    registrarFalha(new FalhaHttpException(FalhaHttpException.Tipo.BLOQUEIO,
                            "BLOQUEIO ANTI-BOT no InfoJobs"), "InfoJobs");
                    break;
                }

                List<Vaga> vagasPagina = extrairVagasDoHTML(doc);
                if (vagasPagina.isEmpty()) {
                    logger.debug("InfoJobs - Nenhum resultado na página {}", pagina);
                    break;
                }
                vagas.addAll(vagasPagina);
                logger.debug("InfoJobs - Página {}: {} vagas extraídas", pagina, vagasPagina.size());
            } catch (IOException e) {
                registrarFalha(e, "InfoJobs - página " + pagina);
                break;
            }
        }
        return vagas;
    }

    private String construirUrl(String termo, boolean homeOffice, int pagina) {
        String slug = homeOffice
                ? "vagas-de-emprego-" + termo + "-trabalho-home-office.aspx"
                : "vagas-de-emprego-" + termo + ".aspx";
        StringBuilder sb = new StringBuilder(BASE_URL).append("/").append(slug);
        List<String> params = new ArrayList<>();
        params.add("Antiguedad=4"); // últimos 15 dias
        if (pagina > 1) {
            params.add("Page=" + pagina);
        }
        sb.append("?").append(String.join("&", params));
        return sb.toString();
    }

    private List<Vaga> extrairVagasDoHTML(Document doc) {
        List<Vaga> vagas = new ArrayList<>();
        Elements cards = doc.select("div.js_cardLink[data-href]");
        if (cards.isEmpty()) {
            cards = doc.select("div[id^=vacancy]");
        }
        if (cards.isEmpty()) {
            cards = doc.select("div.js_rowCard");
        }
        logger.debug("InfoJobs - Cards encontrados: {}", cards.size());

        for (Element card : cards) {
            try {
                Vaga vaga = extrairVagaDoCard(card);
                if (vaga != null) {
                    vagas.add(vaga);
                }
            } catch (Exception e) {
                logger.debug("InfoJobs - Erro ao extrair card: {}", e.getMessage());
            }
        }
        return vagas;
    }

    private Vaga extrairVagaDoCard(Element card) {
        String titulo = primeiroTexto(card, "h2.js_vacancyTitle", "h2.h3", "h2");
        if (titulo.isEmpty()) {
            return null;
        }

        String empresa = primeiroTexto(card, "a[href*='/empresa-']", ".text-body a.text-body");
        String localizacao = "";
        Element localEl = card.selectFirst("div.mb-8");
        if (localEl != null && localEl.children().isEmpty()) {
            localizacao = localEl.text().trim();
        }
        if (localizacao.isEmpty()) {
            localizacao = primeiroTexto(card, "[class*='location']", "[class*='cidade']");
        }

        String dataTexto = "";
        Element dataHidden = card.selectFirst(".js_date[data-value]");
        if (dataHidden != null) {
            dataTexto = dataHidden.attr("data-value");
        }
        if (dataTexto.isEmpty()) {
            dataTexto = primeiroTexto(card, ".text-medium.small.text-nowrap");
        }

        String urlVaga = card.attr("data-href");
        if (urlVaga.isEmpty()) {
            urlVaga = primeiroHref(card, "a[href*='/vaga-de-']", "a.text-decoration-none");
        }
        urlVaga = absolutizarUrl(urlVaga, BASE_URL);

        String modalidadeHint = card.text();
        String modalidade = detectarModalidade(localizacao + " " + modalidadeHint, "Remoto");

        return montarVaga(titulo, empresa, localizacao, modalidade, dataTexto, urlVaga, "InfoJobs", card.text());
    }
}
