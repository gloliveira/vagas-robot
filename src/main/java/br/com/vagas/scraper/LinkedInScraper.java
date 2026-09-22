package br.com.vagas.scraper;

import br.com.vagas.config.ConfiguracaoBusca;
import br.com.vagas.model.ResultadoBusca;
import br.com.vagas.model.Vaga;
import br.com.vagas.util.DataUtil;
import br.com.vagas.util.FiltroBrasil;
import br.com.vagas.util.HttpUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Scraper para o LinkedIn usando o endpoint público de vagas (guest API).
 * Não requer autenticação ou login.
 *
 * Endpoint base: https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search
 *
 * Parâmetros principais:
 *   - keywords: termos de busca
 *   - location: localização
 *   - f_TPR: filtro de tempo (r1209600 = 2 semanas)
 *   - f_E: nível (4 = Mid-Senior, 3 = Associate/Pleno)
 *   - f_WT: modalidade (2 = Remoto)
 *   - start: paginação (incrementa de 25 em 25)
 */
public class LinkedInScraper implements ScraperVagas {

    private static final Logger logger = LoggerFactory.getLogger(LinkedInScraper.class);
    private IOException ultimaFalha;

    // Endpoint da guest API pública do LinkedIn
    private static final String BASE_URL = "https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search";
    // URL de detalhe de vaga
    private static final String DETALHE_URL = "https://www.linkedin.com/jobs/view/";

    // Filtro de tempo: r1209600 = últimas 2 semanas (14 dias * 24h * 3600s)
    private static final String FILTRO_2_SEMANAS = "r1209600";

    // Nível de experiência: 4 = Mid-Senior Level, 3 = Associate
    private static final String NIVEL_SENIOR = "4";
    private static final String NIVEL_PLENO = "3";

    // Modalidade: 2 = Remoto
    private static final String MODALIDADE_REMOTO = "2";

    // GeoId do Brasil no LinkedIn
    private static final String GEO_ID_BRASIL = "106057199";

    @Override
    public String getNomePlataforma() {
        return "LinkedIn";
    }

    @Override
    public ResultadoBusca buscarVagas() {
        List<Vaga> todasVagas = new ArrayList<>();
        IOException falha = null;
        ultimaFalha = null;
        logger.info("Iniciando busca no LinkedIn...");

        // Termos de busca específicos para Java Senior/Pleno
        List<String> termosBusca = Arrays.asList(
                "Java developer senior",
                "Java Spring Boot developer",
                "JSF",
                "PrimeFaces",
                "Legado",
                "Pl",
                "SR",
                "desenvolvedor Java pleno senior"
        );

        for (String termo : termosBusca) {
            logger.info("LinkedIn - Buscando remotas no Brasil: '{}'", termo);
            List<Vaga> vagasRemoto = buscarComParametros(termo, "Brasil", GEO_ID_BRASIL,
                    MODALIDADE_REMOTO, "Remoto");
            if (vagasRemoto == null) {
                falha = ultimaFalha;
                break;
            }
            todasVagas.addAll(vagasRemoto);
            logger.info("LinkedIn - Encontradas {} vagas remotas para '{}'", vagasRemoto.size(), termo);
        }

        logger.info("LinkedIn - Total de vagas encontradas: {}", todasVagas.size());
        return ResultadoBusca.de(getNomePlataforma(), todasVagas, falha);
    }

    private List<Vaga> buscarComParametros(String keywords, String location, String geoId,
                                            String modalidade, String modalidadeLabel) {
        List<Vaga> vagas = new ArrayList<>();
        int start = 0;
        int maxPaginas = 3; // Máximo de 3 páginas (75 vagas) por busca

        for (int pagina = 0; pagina < maxPaginas; pagina++) {
            try {
                String url = construirUrl(keywords, location, geoId, modalidade, start);
                logger.debug("LinkedIn URL: {}", url);

                Document doc = HttpUtil.get(url);
                Elements itensVaga = doc.select("li");

                if (itensVaga.isEmpty()) {
                    logger.debug("LinkedIn - Nenhum resultado na página {}", pagina + 1);
                    break;
                }

                List<Vaga> vagasPagina = new ArrayList<>();
                for (Element item : itensVaga) {
                    Vaga vaga = extrairVaga(item, modalidadeLabel);
                    if (vaga != null) {
                        vagasPagina.add(vaga);
                    }
                }

                vagas.addAll(vagasPagina);
                logger.debug("LinkedIn - Página {}: {} vagas extraídas", pagina + 1, vagasPagina.size());

                if (vagasPagina.isEmpty()) break;
                start += 25;

            } catch (IOException e) {
                ultimaFalha = e;
                logger.error("LinkedIn - Falha na página {}: {}", pagina + 1, e.getMessage());
                return null;
            }
        }

        return vagas;
    }

    private String construirUrl(String keywords, String location, String geoId,
                                 String modalidade, int start) {
        StringBuilder sb = new StringBuilder(BASE_URL);
        sb.append("?keywords=").append(URLEncoder.encode(keywords, StandardCharsets.UTF_8));
        sb.append("&location=").append(URLEncoder.encode(location, StandardCharsets.UTF_8));
        if (geoId != null) {
            sb.append("&geoId=").append(geoId);
        }
        sb.append("&f_TPR=").append(FILTRO_2_SEMANAS);
        // Inclui ambos os níveis (Senior e Pleno/Associate)
        sb.append("&f_E=").append(NIVEL_PLENO).append("%2C").append(NIVEL_SENIOR);
        if (modalidade != null) {
            sb.append("&f_WT=").append(modalidade);
        }
        sb.append("&start=").append(start);
        return sb.toString();
    }

    private Vaga extrairVaga(Element item, String modalidadeLabel) {
        try {
            // Título da vaga
            Element tituloEl = item.selectFirst("h3.base-search-card__title");
            if (tituloEl == null) tituloEl = item.selectFirst(".job-search-card__title");
            if (tituloEl == null) tituloEl = item.selectFirst("h3");
            String titulo = tituloEl != null ? tituloEl.text().trim() : "";

            if (titulo.isEmpty()) return null;

            // Empresa
            Element empresaEl = item.selectFirst("h4.base-search-card__subtitle");
            if (empresaEl == null) empresaEl = item.selectFirst(".job-search-card__company-name");
            if (empresaEl == null) empresaEl = item.selectFirst("h4");
            String empresa = empresaEl != null ? empresaEl.text().trim() : "Não informada";

            // Localização
            Element localEl = item.selectFirst(".job-search-card__location");
            if (localEl == null) localEl = item.selectFirst(".base-search-card__metadata span");
            String localizacao = localEl != null ? localEl.text().trim() : "Não informada";

            // Data de publicação
            Element dataEl = item.selectFirst("time");
            String dataTexto = "";
            if (dataEl != null) {
                dataTexto = dataEl.attr("datetime");
                if (dataTexto.isEmpty()) dataTexto = dataEl.text().trim();
            }

            // URL da vaga
            Element linkEl = item.selectFirst("a.base-card__full-link");
            if (linkEl == null) linkEl = item.selectFirst("a[href*='/jobs/view/']");
            if (linkEl == null) linkEl = item.selectFirst("a");
            String urlVaga = linkEl != null ? linkEl.attr("href") : "";
            if (urlVaga.contains("?")) {
                urlVaga = urlVaga.substring(0, urlVaga.indexOf("?"));
            }

            if (!isVagaRelevante(titulo)) {
                return null;
            }
            if (!FiltroBrasil.isVagaBrasileira(localizacao)) {
                return null;
            }
            if (ConfiguracaoBusca.SOMENTE_REMOTAS) {
                String blob = (titulo + " " + localizacao).toLowerCase();
                if (blob.contains("híbrido") || blob.contains("hibrido") || blob.contains("hybrid")) {
                    return null;
                }
                if ((blob.contains("presencial") || blob.contains("on-site") || blob.contains("onsite"))
                        && !blob.contains("remoto") && !blob.contains("remote") && !blob.contains("home office")) {
                    return null;
                }
            }

            // Verificar data (filtro de 2 semanas)
            LocalDate dataPublicacao = DataUtil.parsearData(dataTexto);
            if (!DataUtil.isDentroDoLimite(dataPublicacao, ConfiguracaoBusca.MAX_DIAS_PUBLICACAO)) {
                return null;
            }

            Vaga vaga = new Vaga();
            vaga.setTitulo(titulo);
            vaga.setEmpresa(empresa);
            vaga.setLocalizacao(localizacao);
            vaga.setModalidade("Remoto");
            vaga.setDataPublicacao(dataTexto.isEmpty() ? "Recente" : dataTexto);
            vaga.setDataPublicacaoDate(dataPublicacao);
            vaga.setUrlVaga(urlVaga.isEmpty() ? "#" : urlVaga);
            vaga.setPlataforma("LinkedIn");
            vaga.setNivel(detectarNivel(titulo));
            vaga.setTecnologias(extrairTecnologias(titulo));

            return vaga;

        } catch (Exception e) {
            logger.debug("LinkedIn - Erro ao extrair vaga: {}", e.getMessage());
            return null;
        }
    }

    private boolean isVagaRelevante(String titulo) {
        if (titulo == null || titulo.isEmpty()) return false;
        String tituloLower = titulo.toLowerCase();
        return tituloLower.contains("java") ||
               tituloLower.contains("spring") ||
               tituloLower.contains("jsf") ||
               tituloLower.contains("backend") ||
               tituloLower.contains("back-end") ||
               tituloLower.contains("back end");
    }

    private String detectarModalidade(String localizacao, String modalidadeLabel) {
        if (localizacao == null) return modalidadeLabel;
        String loc = localizacao.toLowerCase();
        if (loc.contains("remoto") || loc.contains("remote") || loc.contains("home office")) {
            return "Remoto";
        }
        if (loc.contains("híbrido") || loc.contains("hibrido") || loc.contains("hybrid")) {
            return "Híbrido";
        }
        return modalidadeLabel;
    }

    private String detectarNivel(String titulo) {
        if (titulo == null) return "";
        String t = titulo.toLowerCase();
        if (t.contains("senior") || t.contains("sênior") || t.contains("sr.") || t.contains("sr ")) {
            return "Sênior";
        }
        if (t.contains("pleno") || t.contains("mid") || t.contains("pl.") || t.contains("pl ")) {
            return "Pleno";
        }
        return "Não especificado";
    }

    private String extrairTecnologias(String titulo) {
        if (titulo == null) return "";
        List<String> encontradas = new ArrayList<>();
        String tituloLower = titulo.toLowerCase();
        for (String tech : ConfiguracaoBusca.TECNOLOGIAS_RELEVANTES) {
            if (tituloLower.contains(tech.toLowerCase())) {
                encontradas.add(tech);
            }
        }
        return String.join(", ", encontradas);
    }
}
