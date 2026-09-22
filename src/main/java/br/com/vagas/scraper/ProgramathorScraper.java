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
import java.util.List;

/**
 * Scraper para Programathor (vagas de tecnologia).
 * Listagem em https://programathor.com.br/jobs-java
 */
public class ProgramathorScraper extends ScraperBase {

    private static final Logger logger = LoggerFactory.getLogger(ProgramathorScraper.class);
    private static final String BASE_URL = "https://programathor.com.br";
    private static final String LISTA_URL = BASE_URL + "/jobs-java";
    private static final String REFERER = "https://programathor.com.br/";

    @Override
    public String getNomePlataforma() {
        return "Programathor";
    }

    @Override
    public ResultadoBusca buscarVagas() {
        List<Vaga> todasVagas = new ArrayList<>();
        falhaHttp = null;
        logger.info("Iniciando busca no Programathor...");

        int maxPaginas = 3;
        for (int pagina = 1; pagina <= maxPaginas; pagina++) {
            try {
                String url = pagina == 1 ? LISTA_URL : LISTA_URL + "?page=" + pagina;
                logger.debug("Programathor URL: {}", url);

                Document doc = HttpUtil.get(url, REFERER);
                if (documentoBloqueado(doc)) {
                    registrarFalha(new FalhaHttpException(FalhaHttpException.Tipo.BLOQUEIO,
                            "BLOQUEIO ANTI-BOT no Programathor"), "Programathor");
                    break;
                }

                List<Vaga> vagasPagina = extrairVagasDoHTML(doc);
                if (vagasPagina.isEmpty()) {
                    logger.debug("Programathor - Nenhum resultado na página {}", pagina);
                    break;
                }
                todasVagas.addAll(vagasPagina);
                logger.debug("Programathor - Página {}: {} vagas extraídas", pagina, vagasPagina.size());
            } catch (IOException e) {
                registrarFalha(e, "Programathor - página " + pagina);
                break;
            }
        }

        logger.info("Programathor - Total de vagas encontradas: {}", todasVagas.size());
        return fecharResultado(todasVagas);
    }

    private List<Vaga> extrairVagasDoHTML(Document doc) {
        List<Vaga> vagas = new ArrayList<>();
        Elements cards = doc.select("div.cell-list");
        logger.debug("Programathor - Cards encontrados: {}", cards.size());

        for (Element card : cards) {
            try {
                Vaga vaga = extrairVagaDoCard(card);
                if (vaga != null) {
                    vagas.add(vaga);
                }
            } catch (Exception e) {
                logger.debug("Programathor - Erro ao extrair card: {}", e.getMessage());
            }
        }
        return vagas;
    }

    private Vaga extrairVagaDoCard(Element card) {
        if (card.text().toLowerCase().contains("vencida")) {
            return null;
        }

        Element link = card.selectFirst("a[href^=/jobs/]");
        if (link == null) {
            return null;
        }

        String titulo = primeiroTexto(card, "h3");
        if (titulo.isEmpty()) {
            return null;
        }
        titulo = titulo.replace("📍 PRESENCIAL - SOMENTE PARA CANDIDATOS NO LOCAL", "").trim();

        String empresa = "";
        String localizacao = "";
        String nivel = "";
        for (Element span : card.select("div.cell-list-content-icon span")) {
            String txt = span.text().trim();
            if (span.selectFirst("i.fa-briefcase") != null || span.html().contains("fa-briefcase")) {
                empresa = txt;
            } else if (span.selectFirst(".fa-map-marker-alt") != null || span.html().contains("map-marker")) {
                localizacao = txt;
            } else if (span.selectFirst(".fa-chart-bar") != null || span.html().contains("chart-bar")) {
                nivel = txt;
            }
        }

        String techs = "";
        List<String> tags = new ArrayList<>();
        for (Element tag : card.select("span.tag-list")) {
            tags.add(tag.text().trim());
        }
        if (!tags.isEmpty()) {
            techs = String.join(", ", tags);
        }

        String urlVaga = absolutizarUrl(link.attr("href"), BASE_URL);
        Vaga vaga = montarVaga(titulo, empresa, localizacao, null, "Recente", urlVaga, "Programathor", techs);
        if (vaga != null) {
            if (!nivel.isEmpty() && "Não especificado".equals(vaga.getNivel())) {
                vaga.setNivel(detectarNivel(nivel + " " + titulo));
            }
            if (vaga.getTecnologias() == null || vaga.getTecnologias().isEmpty()) {
                vaga.setTecnologias(techs);
            }
        }
        return vaga;
    }
}
