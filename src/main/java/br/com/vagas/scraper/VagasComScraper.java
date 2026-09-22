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
 * Scraper para Vagas.com.br.
 * Listagem server-side em li.vaga com a.link-detalhes-vaga.
 */
public class VagasComScraper extends ScraperBase {

    private static final Logger logger = LoggerFactory.getLogger(VagasComScraper.class);
    private static final String BASE_URL = "https://www.vagas.com.br";
    private static final String REFERER = "https://www.vagas.com.br/";

    @Override
    public String getNomePlataforma() {
        return "Vagas.com";
    }

    @Override
    public ResultadoBusca buscarVagas() {
        List<Vaga> todasVagas = new ArrayList<>();
        falhaHttp = null;
        logger.info("Iniciando busca no Vagas.com.br...");

        List<String> caminhos = Arrays.asList(
                "vagas-de-desenvolvedor-java",
                "vagas-de-java-spring-boot",
                "vagas-de-desenvolvedor-java-pleno",
                "vagas-de-desenvolvedor-java-senior"
        );

        for (String caminho : caminhos) {
            logger.info("Vagas.com - Buscando: '{}'", caminho);
            List<Vaga> vagas = buscarCaminho(caminho);
            todasVagas.addAll(vagas);
            logger.info("Vagas.com - Encontradas {} vagas para '{}'", vagas.size(), caminho);
        }

        logger.info("Vagas.com - Total de vagas encontradas: {}", todasVagas.size());
        return fecharResultado(todasVagas);
    }

    private List<Vaga> buscarCaminho(String caminho) {
        List<Vaga> vagas = new ArrayList<>();
        int maxPaginas = 3;

        for (int pagina = 1; pagina <= maxPaginas; pagina++) {
            try {
                String url = BASE_URL + "/" + caminho
                        + (pagina > 1 ? "?pagina=" + pagina + "&ordenar_por=mais_recentes" : "?ordenar_por=mais_recentes");
                logger.debug("Vagas.com URL: {}", url);

                Document doc = HttpUtil.get(url, REFERER);
                if (documentoBloqueado(doc)) {
                    registrarFalha(new FalhaHttpException(FalhaHttpException.Tipo.BLOQUEIO,
                            "BLOQUEIO ANTI-BOT no Vagas.com"), "Vagas.com");
                    break;
                }

                List<Vaga> vagasPagina = extrairVagasDoHTML(doc);
                if (vagasPagina.isEmpty()) {
                    logger.debug("Vagas.com - Nenhum resultado na página {}", pagina);
                    break;
                }
                vagas.addAll(vagasPagina);
                logger.debug("Vagas.com - Página {}: {} vagas extraídas", pagina, vagasPagina.size());
            } catch (IOException e) {
                registrarFalha(e, "Vagas.com - página " + pagina);
                break;
            }
        }
        return vagas;
    }

    private List<Vaga> extrairVagasDoHTML(Document doc) {
        List<Vaga> vagas = new ArrayList<>();
        Elements cards = doc.select("li.vaga");
        if (cards.isEmpty()) {
            cards = doc.select("#todasVagas li");
        }
        logger.debug("Vagas.com - Cards encontrados: {}", cards.size());

        for (Element card : cards) {
            try {
                Vaga vaga = extrairVagaDoCard(card);
                if (vaga != null) {
                    vagas.add(vaga);
                }
            } catch (Exception e) {
                logger.debug("Vagas.com - Erro ao extrair card: {}", e.getMessage());
            }
        }
        return vagas;
    }

    private Vaga extrairVagaDoCard(Element card) {
        Element link = card.selectFirst("a.link-detalhes-vaga");
        String titulo = link != null ? link.attr("title") : "";
        if (titulo.isEmpty()) {
            titulo = primeiroTexto(card, "h2.cargo", "h2");
        }
        if (titulo.isEmpty()) {
            return null;
        }

        String empresa = primeiroTexto(card, "span.emprVaga", ".informacoes-header span");
        String localizacao = primeiroTexto(card, "span.vaga-local", ".local");
        String dataTexto = primeiroTexto(card, "span.data-publicacao");
        String nivel = primeiroTexto(card, "span.nivelVaga");
        String urlVaga = link != null ? link.attr("href") : primeiroHref(card, "a");
        urlVaga = absolutizarUrl(urlVaga, BASE_URL);

        String descricao = primeiroTexto(card, "div.detalhes p", "div.detalhes");
        Vaga vaga = montarVaga(titulo, empresa, localizacao, null, dataTexto, urlVaga, "Vagas.com",
                descricao + " " + nivel);
        if (vaga != null && !nivel.isEmpty() && "Não especificado".equals(vaga.getNivel())) {
            vaga.setNivel(detectarNivel(nivel + " " + titulo));
        }
        return vaga;
    }
}
