package br.com.vagas.scraper;

import br.com.vagas.config.ConfiguracaoBusca;
import br.com.vagas.model.ResultadoBusca;
import br.com.vagas.model.Vaga;
import br.com.vagas.util.DataUtil;
import br.com.vagas.util.FalhaHttpException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    // Detalhe da vaga na guest API (descrição completa)
    private static final String DETALHE_API_URL = "https://www.linkedin.com/jobs-guest/jobs/api/jobPosting/";
    private static final Pattern JOB_ID_URN = Pattern.compile("jobPosting:(\\d+)");
    private static final Pattern JOB_ID_URL = Pattern.compile("(\\d{6,})/?$");

    // Filtro de tempo: r1209600 = últimas 2 semanas (14 dias * 24h * 3600s)
    private static final String FILTRO_2_SEMANAS = "r1209600";

    // Nível de experiência: 4 = Mid-Senior Level, 3 = Associate
    private static final String NIVEL_SENIOR = "4";
    private static final String NIVEL_PLENO = "3";

    // Modalidade: 2 = Remoto
    private static final String MODALIDADE_REMOTO = "2";

    // GeoId do Brasil no LinkedIn
    private static final String GEO_ID_BRASIL = "106057199";

    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final long[] ESPERAS_429_MS = {15_000, 30_000};
    private static final long PAUSA_ENTRE_TERMOS_MS = 4_000;

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

        for (int i = 0; i < termosBusca.size(); i++) {
            String termo = termosBusca.get(i);
            if (i > 0) {
                pausar(PAUSA_ENTRE_TERMOS_MS);
            }
            logger.info("LinkedIn - Buscando remotas no Brasil: '{}'", termo);
            List<Vaga> vagasRemoto = buscarComParametros(termo, "Brasil", GEO_ID_BRASIL,
                    MODALIDADE_REMOTO, "Remoto");
            todasVagas.addAll(vagasRemoto);
            logger.info("LinkedIn - Encontradas {} vagas remotas para '{}'", vagasRemoto.size(), termo);
            if (ultimaFalha != null) {
                falha = ultimaFalha;
                break;
            }
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

                Document doc = getComRetentativa(url);
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
                break;
            }
        }

        return vagas;
    }

    private Document getComRetentativa(String url) throws IOException {
        for (int tentativa = 0; ; tentativa++) {
            try {
                return HttpUtil.get(url);
            } catch (FalhaHttpException e) {
                if (e.getHttpStatus() != HTTP_TOO_MANY_REQUESTS || tentativa >= ESPERAS_429_MS.length) {
                    throw e;
                }
                long espera = ESPERAS_429_MS[tentativa];
                logger.warn("LinkedIn - HTTP 429 (limite de requisições), aguardando {}s antes de tentar novamente",
                        espera / 1000);
                pausar(espera);
            }
        }
    }

    private void pausar(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
                if (mencionaHibridoOuPresencial(blob)) {
                    return null;
                }
            }

            // Verificar data (filtro de 2 semanas)
            LocalDate dataPublicacao = DataUtil.parsearData(dataTexto);
            if (!DataUtil.isDentroDoLimite(dataPublicacao, ConfiguracaoBusca.MAX_DIAS_PUBLICACAO)) {
                return null;
            }

            // O filtro f_WT=2 deixa passar híbridas/presenciais e o card não traz a modalidade:
            // vaga com cidade só entra se a descrição confirmar que é remota.
            if (ConfiguracaoBusca.SOMENTE_REMOTAS && !localizacaoIndicaRemoto(localizacao, titulo)) {
                String jobId = extrairJobId(item, urlVaga);
                if (jobId.isEmpty() || !descricaoConfirmaRemoto(jobId)) {
                    logger.debug("LinkedIn - Descartada (não confirmada como remota): {} | {}", titulo, localizacao);
                    return null;
                }
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

    private boolean mencionaHibridoOuPresencial(String textoMinusculo) {
        return textoMinusculo.contains("híbrido") || textoMinusculo.contains("hibrido")
                || textoMinusculo.contains("hybrid")
                || textoMinusculo.contains("presencial") || textoMinusculo.contains("on-site")
                || textoMinusculo.contains("onsite") || textoMinusculo.contains("on site");
    }

    private boolean mencionaRemoto(String textoMinusculo) {
        return textoMinusculo.contains("remoto") || textoMinusculo.contains("remote")
                || textoMinusculo.contains("home office") || textoMinusculo.contains("home-office")
                || textoMinusculo.contains("trabalho a distância") || textoMinusculo.contains("work from home");
    }

    /** Vagas remotas no LinkedIn costumam ter só o país como local. */
    private boolean localizacaoIndicaRemoto(String localizacao, String titulo) {
        String loc = localizacao == null ? "" : localizacao.trim().toLowerCase();
        if (loc.equals("brazil") || loc.equals("brasil")) {
            return true;
        }
        return mencionaRemoto(loc + " " + (titulo == null ? "" : titulo.toLowerCase()));
    }

    private String extrairJobId(Element item, String urlVaga) {
        Element urnEl = item.selectFirst("[data-entity-urn]");
        if (urnEl != null) {
            Matcher m = JOB_ID_URN.matcher(urnEl.attr("data-entity-urn"));
            if (m.find()) {
                return m.group(1);
            }
        }
        Matcher m = JOB_ID_URL.matcher(urlVaga == null ? "" : urlVaga);
        return m.find() ? m.group(1) : "";
    }

    private boolean descricaoConfirmaRemoto(String jobId) {
        try {
            Document detalhe = getComRetentativa(DETALHE_API_URL + jobId);
            Element descricaoEl = detalhe.selectFirst(".show-more-less-html__markup");
            if (descricaoEl == null) descricaoEl = detalhe.selectFirst(".description__text");
            String texto = (descricaoEl != null ? descricaoEl.text() : detalhe.text()).toLowerCase();
            return mencionaRemoto(texto) && !mencionaHibridoOuPresencial(texto);
        } catch (IOException e) {
            logger.warn("LinkedIn - Não foi possível abrir o detalhe da vaga {}: {}", jobId, e.getMessage());
            return false;
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
