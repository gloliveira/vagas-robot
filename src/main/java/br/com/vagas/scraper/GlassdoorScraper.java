package br.com.vagas.scraper;

import br.com.vagas.config.ConfiguracaoBusca;
import br.com.vagas.model.ResultadoBusca;
import br.com.vagas.model.Vaga;
import br.com.vagas.util.FalhaHttpException;
import br.com.vagas.util.HttpUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scraper para o Glassdoor Brasil ({@code https://www.glassdoor.com.br/Vaga}).
 * A listagem costuma vir em cards HTML ({@code data-test=jobListing}) e/ou no
 * JSON embutido ({@code __NEXT_DATA__} / Apollo). O site também usa Cloudflare.
 */
public class GlassdoorScraper extends ScraperBase {

    private static final Logger logger = LoggerFactory.getLogger(GlassdoorScraper.class);
    private static final String BASE_URL = "https://www.glassdoor.com.br";
    private static final String BUSCA_URL = BASE_URL + "/Vaga/jobs.htm";
    private static final String REFERER = BASE_URL + "/Vaga/";
    /** País Brasil no Glassdoor (locT=N, locId=36 / slug IN36). */
    private static final int LOC_ID_BRASIL = 36;

    @Override
    public String getNomePlataforma() {
        return "Glassdoor";
    }

    @Override
    public ResultadoBusca buscarVagas() {
        List<Vaga> todasVagas = new ArrayList<>();
        Set<String> vistas = new LinkedHashSet<>();
        falhaHttp = null;
        logger.info("Iniciando busca no Glassdoor Brasil...");

        List<String> termos = Arrays.asList(
                "desenvolvedor Java senior",
                "Java Spring Boot",
                "Java developer pleno"
        );

        for (String termo : termos) {
            logger.info("Glassdoor - Buscando remotas no Brasil: '{}'", termo);
            List<Vaga> vagasRemoto = buscarComParametros(termo, vistas);
            todasVagas.addAll(vagasRemoto);
            logger.info("Glassdoor - Encontradas {} vagas remotas para '{}'", vagasRemoto.size(), termo);

            if (vagasRemoto.isEmpty() && falhaHttp != null) {
                break;
            }
        }

        logger.info("Glassdoor - Total de vagas encontradas: {}", todasVagas.size());
        return fecharResultado(todasVagas);
    }

    private List<Vaga> buscarComParametros(String query, Set<String> vistas) {
        List<Vaga> vagas = new ArrayList<>();
        int maxPaginas = 2;

        for (int pagina = 1; pagina <= maxPaginas; pagina++) {
            try {
                String url = construirUrl(query, pagina);
                logger.debug("Glassdoor URL: {}", url);

                Document doc = HttpUtil.get(url, REFERER);
                if (documentoBloqueado(doc) || paginaDeLoginOuDesafio(doc)
                        || HttpUtil.isPaginaDeBloqueio(doc, doc.html())) {
                    registrarFalha(new FalhaHttpException(FalhaHttpException.Tipo.BLOQUEIO,
                            "BLOQUEIO ANTI-BOT no Glassdoor (Cloudflare/CAPTCHA/login)"), "Glassdoor");
                    break;
                }

                List<Vaga> vagasPagina = extrairVagasDoHTML(doc);
                if (vagasPagina.isEmpty()) {
                    vagasPagina = extrairVagasDoJson(doc);
                }
                if (vagasPagina.isEmpty()) {
                    logger.debug("Glassdoor - Nenhum resultado na página {}", pagina);
                    break;
                }
                int novas = 0;
                for (Vaga vaga : vagasPagina) {
                    if (vistas.add(chaveVaga(vaga))) {
                        vagas.add(vaga);
                        novas++;
                    }
                }
                logger.debug("Glassdoor - Página {}: {} vagas extraídas, {} novas", pagina, vagasPagina.size(), novas);
                if (novas == 0) {
                    break;
                }
            } catch (IOException e) {
                registrarFalha(e, "Glassdoor - página " + pagina);
                break;
            }
        }
        return vagas;
    }

    private String construirUrl(String query, int pagina) {
        StringBuilder sb = new StringBuilder(BUSCA_URL);
        sb.append("?sc.keyword=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        sb.append("&locT=N");
        sb.append("&locId=").append(LOC_ID_BRASIL);
        sb.append("&fromAge=").append(fromAgeGlassdoor());
        if (ConfiguracaoBusca.SOMENTE_REMOTAS) {
            sb.append("&remoteWorkType=1");
        }
        if (pagina > 1) {
            sb.append("&p=").append(pagina);
        }
        return sb.toString();
    }

    /**
     * Glassdoor só aceita 1, 3, 7, 14 ou 30 dias. Usa o menor valor que cobre o limite configurado.
     */
    private int fromAgeGlassdoor() {
        int dias = ConfiguracaoBusca.MAX_DIAS_PUBLICACAO;
        if (dias <= 1) {
            return 1;
        }
        if (dias <= 3) {
            return 3;
        }
        if (dias <= 7) {
            return 7;
        }
        if (dias <= 14) {
            return 14;
        }
        return 30;
    }

    private boolean paginaDeLoginOuDesafio(Document doc) {
        String titulo = doc.title() != null ? doc.title().toLowerCase() : "";
        if (titulo.contains("sign in") || titulo.contains("captcha") || titulo.contains("security check")) {
            return true;
        }
        boolean temListagem = doc.selectFirst("[data-test=jobListing]") != null
                || doc.selectFirst("script#__NEXT_DATA__") != null
                || doc.html().contains("jobTitleText")
                || doc.html().contains("jobListings");
        if (temListagem) {
            return false;
        }
        String texto = doc.text() != null ? doc.text().toLowerCase() : "";
        return texto.contains("please sign in")
                || texto.contains("please verify you are a human")
                || texto.contains("create an account to continue");
    }

    private List<Vaga> extrairVagasDoHTML(Document doc) {
        List<Vaga> vagas = new ArrayList<>();
        Elements cards = doc.select("[data-test=jobListing]");
        if (cards.isEmpty()) {
            cards = doc.select("li[data-test=jl], li.react-job-listing");
        }
        if (cards.isEmpty()) {
            cards = doc.select("[class*=JobCard_jobCardWrapper], [class*=JobCard_jobCardContainer]");
        }
        logger.debug("Glassdoor - Cards HTML encontrados: {}", cards.size());

        Set<String> vistas = new LinkedHashSet<>();
        for (Element card : cards) {
            try {
                Vaga vaga = extrairVagaDoCard(card);
                if (vaga != null && vistas.add(chaveVaga(vaga))) {
                    vagas.add(vaga);
                }
            } catch (Exception e) {
                logger.debug("Glassdoor - Erro ao extrair card: {}", e.getMessage());
            }
        }
        return vagas;
    }

    private Vaga extrairVagaDoCard(Element card) {
        String titulo = primeiroTexto(card,
                "[data-test=job-title]",
                "a[data-test=job-link]",
                "[class*=JobCard_jobTitle]",
                "a[class*=JobCard]",
                "h2 a",
                "h2");
        if (titulo.isEmpty()) {
            return null;
        }

        String empresa = removerNota(primeiroTexto(card,
                "[data-test=employer-name]",
                "[class*=JobCard_employerName]",
                "[class*=employerName]",
                ".employerName"));
        String localizacao = primeiroTexto(card,
                "[data-test=emp-location]",
                "[class*=JobCard_location]",
                "[class*=location]",
                ".location");
        if (localizacao.isEmpty()) {
            localizacao = "Remoto - Brasil";
        }
        String dataTexto = primeiroTexto(card, "[data-test=job-age]", "[class*=listingAge]", "[class*=JobCard_listingAge]");
        String urlRelativa = primeiroHref(card,
                "a[data-test=job-title]",
                "a[data-test=job-link]",
                "a[href*=jobListing]",
                "a[href*=job-listing]",
                "a[href*=/partner/jobListing]",
                "a[href*=/vaga]",
                "a");
        String urlVaga = absolutizarUrl(urlRelativa, BASE_URL);
        String extra = card.text();
        return montarVaga(titulo, empresa, localizacao, "Remoto", dataTexto, urlVaga, "Glassdoor", extra);
    }

    private List<Vaga> extrairVagasDoJson(Document doc) {
        List<Vaga> vagas = new ArrayList<>();
        Set<String> vistas = new LinkedHashSet<>();
        List<JsonObject> raizes = new ArrayList<>();

        Element nextData = doc.selectFirst("script#__NEXT_DATA__");
        if (nextData != null && !nextData.data().isBlank()) {
            adicionarJsonSeValido(raizes, nextData.data());
        }

        String html = doc.html();
        String apollo = extrairObjetoJsonApos(html, "\"apolloState\":");
        if (apollo == null) {
            apollo = extrairObjetoJsonApos(html, "\"apolloCache\":");
        }
        if (apollo != null) {
            adicionarJsonSeValido(raizes, apollo);
        }

        for (Element script : doc.select("script")) {
            String corpo = script.data();
            if (corpo != null && (corpo.contains("jobTitleText") || corpo.contains("jobListings")
                    || corpo.contains("listingId"))) {
                adicionarJsonSeValido(raizes, corpo.trim());
            }
        }

        for (JsonObject raiz : raizes) {
            coletarVagasDoJson(raiz, vagas, vistas);
        }
        logger.debug("Glassdoor - Vagas extraídas do JSON: {}", vagas.size());
        return vagas;
    }

    private void adicionarJsonSeValido(List<JsonObject> raizes, String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return;
        }
        try {
            JsonElement el = JsonParser.parseString(bruto);
            if (el != null && el.isJsonObject()) {
                raizes.add(el.getAsJsonObject());
            }
        } catch (Exception ignored) {
            // trecho de script que não é JSON puro
        }
    }

    private void coletarVagasDoJson(JsonElement el, List<Vaga> vagas, Set<String> vistas) {
        if (el == null || el.isJsonNull()) {
            return;
        }
        if (el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                coletarVagasDoJson(item, vagas, vistas);
            }
            return;
        }
        if (!el.isJsonObject()) {
            return;
        }
        JsonObject obj = el.getAsJsonObject();
        if (obj.has("__ref") && obj.size() == 1) {
            return;
        }

        Vaga vaga = tentarExtrairVagaJson(obj);
        if (vaga != null && vistas.add(chaveVaga(vaga))) {
            vagas.add(vaga);
        }

        for (Map.Entry<String, JsonElement> entrada : obj.entrySet()) {
            coletarVagasDoJson(entrada.getValue(), vagas, vistas);
        }
    }

    private Vaga tentarExtrairVagaJson(JsonObject obj) {
        String typename = textoJson(obj, "__typename");
        boolean pareceVaga = "JobViewHeader".equals(typename)
                || "JobListing".equals(typename)
                || obj.has("jobTitleText")
                || (temChave(obj, "listingId", "jobListingId") && temChave(obj, "title", "jobTitle", "normalizedJobTitle"));
        if (!pareceVaga) {
            return null;
        }

        String titulo = textoJson(obj, "jobTitleText", "title", "jobTitle", "normalizedJobTitle");
        if (titulo.isEmpty()) {
            return null;
        }

        String empresa = empresaDoJson(obj);
        String localizacao = textoJson(obj, "locationName", "location");
        if (localizacao.isEmpty()) {
            localizacao = "Remoto - Brasil";
        }

        String dataTexto = dataDoJson(obj);
        String urlVaga = urlDoJson(obj);
        String extra = textoJson(obj, "descriptionSnippet", "goc");
        return montarVaga(titulo, empresa, localizacao, "Remoto", dataTexto, urlVaga, "Glassdoor", extra);
    }

    private String empresaDoJson(JsonObject obj) {
        if (obj.has("employer") && obj.get("employer").isJsonObject()) {
            String nome = textoJson(obj.getAsJsonObject("employer"), "name", "shortName");
            if (!nome.isEmpty()) {
                return removerNota(nome);
            }
        }
        return removerNota(textoJson(obj, "employerName", "companyName", "divisionEmployerName"));
    }

    private String dataDoJson(JsonObject obj) {
        if (obj.has("ageInDays") && obj.get("ageInDays").isJsonPrimitive()
                && obj.get("ageInDays").getAsJsonPrimitive().isNumber()) {
            int dias = obj.get("ageInDays").getAsInt();
            return dias <= 0 ? "hoje" : dias + " dias";
        }
        return textoJson(obj, "discoverDate");
    }

    private String urlDoJson(JsonObject obj) {
        String url = textoJson(obj, "seoUrl", "jobLink", "url", "applyUrl");
        if (!url.isEmpty()) {
            return absolutizarUrl(url, BASE_URL);
        }
        String listingId = textoJson(obj, "listingId", "jobListingId");
        if (listingId.isEmpty() && obj.has("jobListingAdminDetails")
                && obj.get("jobListingAdminDetails").isJsonObject()) {
            listingId = textoJson(obj.getAsJsonObject("jobListingAdminDetails"), "jobListingId");
        }
        if (listingId.isEmpty() && obj.has("job") && obj.get("job").isJsonObject()) {
            listingId = textoJson(obj.getAsJsonObject("job"), "listingId");
        }
        if (!listingId.isEmpty()) {
            return BASE_URL + "/partner/jobListing.htm?jobListingId=" + listingId;
        }
        return "#";
    }

    private boolean temChave(JsonObject obj, String... chaves) {
        for (String chave : chaves) {
            if (obj.has(chave) && !obj.get(chave).isJsonNull()) {
                return true;
            }
        }
        return false;
    }

    private String textoJson(JsonObject obj, String... chaves) {
        for (String chave : chaves) {
            if (obj.has(chave) && !obj.get(chave).isJsonNull()) {
                JsonElement el = obj.get(chave);
                if (el.isJsonPrimitive()) {
                    String v = el.getAsString();
                    if (v != null && !v.isBlank()) {
                        return v.trim();
                    }
                }
            }
        }
        return "";
    }

    private String chaveVaga(Vaga vaga) {
        return (vaga.getTitulo() + "|" + vaga.getEmpresa()).toLowerCase();
    }

    /** O card junta a nota da empresa ao nome (ex.: "Groove Tech 4,4"). */
    private String removerNota(String empresa) {
        return empresa.replaceAll("\\s*\\d[,.]\\d\\s*★?\\s*$", "").trim();
    }

    /**
     * Extrai o objeto JSON que começa logo após o marcador, contando chaves.
     */
    private String extrairObjetoJsonApos(String html, String marcador) {
        int inicioMarcador = html.indexOf(marcador);
        if (inicioMarcador < 0) {
            return null;
        }
        int inicio = html.indexOf('{', inicioMarcador + marcador.length() - 1);
        if (inicio < 0) {
            return null;
        }
        int nivel = 0;
        boolean emString = false;
        boolean escape = false;
        for (int i = inicio; i < html.length(); i++) {
            char c = html.charAt(i);
            if (emString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    emString = false;
                }
                continue;
            }
            if (c == '"') {
                emString = true;
            } else if (c == '{') {
                nivel++;
            } else if (c == '}') {
                nivel--;
                if (nivel == 0) {
                    return html.substring(inicio, i + 1);
                }
            }
        }
        return null;
    }
}
