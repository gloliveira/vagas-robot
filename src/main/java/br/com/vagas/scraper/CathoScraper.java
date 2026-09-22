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
 * Scraper para o Catho (www.catho.com.br).
 * A listagem é HTML server-side: article.offer dentro de li[data-offer-item].
 */
public class CathoScraper extends ScraperBase {

    private static final Logger logger = LoggerFactory.getLogger(CathoScraper.class);
    private static final String BASE_URL = "https://www.catho.com.br";
    private static final String REFERER = "https://www.catho.com.br/vagas/";

    @Override
    public String getNomePlataforma() {
        return "Catho";
    }

    @Override
    public ResultadoBusca buscarVagas() {
        List<Vaga> todasVagas = new ArrayList<>();
        falhaHttp = null;
        logger.info("Iniciando busca no Catho...");

        List<String> termos = Arrays.asList(
                "desenvolvedor-java",
                "java-spring-boot",
                "desenvolvedor-java-pleno",
                "desenvolvedor-java-senior"
        );

        for (String termo : termos) {
            logger.info("Catho - Buscando: '{}'", termo);
            List<Vaga> vagas = buscarTermo(termo);
            todasVagas.addAll(vagas);
            logger.info("Catho - Encontradas {} vagas para '{}'", vagas.size(), termo);
        }

        logger.info("Catho - Total de vagas encontradas: {}", todasVagas.size());
        return fecharResultado(todasVagas);
    }

    private List<Vaga> buscarTermo(String termo) {
        List<Vaga> vagas = new ArrayList<>();
        int maxPaginas = 3;

        for (int pagina = 1; pagina <= maxPaginas; pagina++) {
            try {
                String url = BASE_URL + "/vagas/" + termo + "/" + (pagina > 1 ? "?page=" + pagina : "");
                logger.debug("Catho URL: {}", url);

                Document doc = HttpUtil.get(url, REFERER);
                if (documentoBloqueado(doc)) {
                    registrarFalha(new FalhaHttpException(FalhaHttpException.Tipo.BLOQUEIO,
                            "BLOQUEIO ANTI-BOT no Catho"), "Catho");
                    break;
                }

                List<Vaga> vagasPagina = extrairVagasDoHTML(doc);
                if (vagasPagina.isEmpty()) {
                    logger.debug("Catho - Nenhum resultado na página {}", pagina);
                    break;
                }
                vagas.addAll(vagasPagina);
                logger.debug("Catho - Página {}: {} vagas extraídas", pagina, vagasPagina.size());
            } catch (IOException e) {
                registrarFalha(e, "Catho - página " + pagina);
                break;
            }
        }
        return vagas;
    }

    private List<Vaga> extrairVagasDoHTML(Document doc) {
        List<Vaga> vagas = new ArrayList<>();
        Elements cards = doc.select("li[data-offer-item] article.offer");
        if (cards.isEmpty()) {
            cards = doc.select("article.offer");
        }
        if (cards.isEmpty()) {
            cards = doc.select("article[data-offer-item-subcontainer]");
        }
        logger.debug("Catho - Cards encontrados: {}", cards.size());

        for (Element card : cards) {
            try {
                Vaga vaga = extrairVagaDoCard(card);
                if (vaga != null) {
                    vagas.add(vaga);
                }
            } catch (Exception e) {
                logger.debug("Catho - Erro ao extrair card: {}", e.getMessage());
            }
        }
        return vagas;
    }

    private Vaga extrairVagaDoCard(Element card) {
        Element tituloEl = card.selectFirst("h2.title_offer a");
        if (tituloEl == null) {
            tituloEl = card.selectFirst("a[data-navigation-offer]");
        }
        if (tituloEl == null) {
            tituloEl = card.selectFirst("h2 a");
        }
        String titulo = texto(tituloEl);
        if (titulo.isEmpty()) {
            return null;
        }

        String empresa = primeiroTexto(card, "p.mb-2 span.text-12", "span.text-12");
        if (empresa.toLowerCase().contains("por que")) {
            empresa = empresa.replaceAll("(?i)\\s*por que\\??", "").trim();
        }

        String localizacao = "";
        Element localIcon = card.selectFirst(".i_job_location");
        if (localIcon != null && localIcon.parent() != null) {
            localizacao = localIcon.parent().text().replaceAll("(?i)\\d+\\s*vagas?", "").replace("-", " ").trim();
        }
        if (localizacao.isEmpty()) {
            localizacao = primeiroTexto(card, "[class*='location']", "[class*='local']");
        }

        String dataTexto = primeiroTexto(card, "span.tag.pub_ontem", "span.tag");
        String urlVaga = tituloEl != null ? tituloEl.attr("href") : "";
        urlVaga = absolutizarUrl(urlVaga, BASE_URL);

        String modalidade = localizacao.toLowerCase().contains("trabalhe de casa")
                || titulo.toLowerCase().contains("trabalhe de casa")
                ? "Remoto" : null;

        return montarVaga(titulo, empresa, localizacao, modalidade, dataTexto, urlVaga, "Catho", "");
    }
}
