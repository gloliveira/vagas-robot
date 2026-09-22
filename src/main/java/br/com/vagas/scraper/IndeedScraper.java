package br.com.vagas.scraper;

import br.com.vagas.model.ResultadoBusca;
import br.com.vagas.model.Vaga;
import br.com.vagas.util.FalhaHttpException;
import br.com.vagas.util.HttpUtil;
import com.google.gson.JsonArray;
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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scraper para o Indeed Brasil.
 * O Indeed protege a busca com Cloudflare; quando o HTML chega, os cards
 * estão em mosaic JSON ou em div[data-jk].
 */
public class IndeedScraper extends ScraperBase {

    private static final Logger logger = LoggerFactory.getLogger(IndeedScraper.class);
    private static final String BASE_URL = "https://br.indeed.com/jobs";
    private static final String REFERER = "https://br.indeed.com/";
    private static final Pattern MOSAIC_JSON = Pattern.compile(
            "window\\.mosaic\\.providerData\\[\"mosaic-provider-jobcards\"\\]\\s*=\\s*(\\{.*?\\});",
            Pattern.DOTALL);

    @Override
    public String getNomePlataforma() {
        return "Indeed";
    }

    @Override
    public ResultadoBusca buscarVagas() {
        List<Vaga> todasVagas = new ArrayList<>();
        falhaHttp = null;
        logger.info("Iniciando busca no Indeed Brasil...");

        List<String> termos = Arrays.asList(
                "desenvolvedor Java senior",
                "Java Spring Boot",
                "Java developer pleno"
        );

        for (String termo : termos) {
            logger.info("Indeed - Buscando remotas no Brasil: '{}'", termo);
            List<Vaga> vagasRemoto = buscarComParametros(termo, "Brasil", true);
            todasVagas.addAll(vagasRemoto);
            logger.info("Indeed - Encontradas {} vagas remotas para '{}'", vagasRemoto.size(), termo);

            if (vagasRemoto.isEmpty() && falhaHttp != null) {
                break;
            }
        }

        logger.info("Indeed - Total de vagas encontradas: {}", todasVagas.size());
        return fecharResultado(todasVagas);
    }

    private List<Vaga> buscarComParametros(String query, String location, boolean remoto) {
        List<Vaga> vagas = new ArrayList<>();
        int start = 0;
        int maxPaginas = 2;

        for (int pagina = 0; pagina < maxPaginas; pagina++) {
            try {
                String url = construirUrl(query, location, remoto, start);
                logger.debug("Indeed URL: {}", url);

                Document doc = HttpUtil.get(url, REFERER);
                if (documentoBloqueado(doc) || HttpUtil.isPaginaDeBloqueio(doc, doc.html())) {
                    registrarFalha(new FalhaHttpException(FalhaHttpException.Tipo.BLOQUEIO,
                            "BLOQUEIO ANTI-BOT no Indeed (Cloudflare/CAPTCHA)"), "Indeed");
                    break;
                }

                String modalidade = remoto ? "Remoto" : "Presencial/Híbrido";
                List<Vaga> vagasPagina = extrairVagasDoHTML(doc, modalidade);
                if (vagasPagina.isEmpty()) {
                    vagasPagina = extrairVagasDoMosaic(doc.html(), modalidade);
                }
                if (vagasPagina.isEmpty()) {
                    logger.debug("Indeed - Nenhum resultado na página {}", pagina + 1);
                    break;
                }
                vagas.addAll(vagasPagina);
                logger.debug("Indeed - Página {}: {} vagas extraídas", pagina + 1, vagasPagina.size());
                start += 10;
            } catch (IOException e) {
                registrarFalha(e, "Indeed - página " + (pagina + 1));
                break;
            }
        }
        return vagas;
    }

    private String construirUrl(String query, String location, boolean remoto, int start) {
        StringBuilder sb = new StringBuilder(BASE_URL);
        sb.append("?q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        sb.append("&l=").append(URLEncoder.encode(location, StandardCharsets.UTF_8));
        sb.append("&fromage=14");
        if (remoto) {
            sb.append("&remotejob=1");
        }
        if (start > 0) {
            sb.append("&start=").append(start);
        }
        return sb.toString();
    }

    private List<Vaga> extrairVagasDoHTML(Document doc, String modalidadeDefault) {
        List<Vaga> vagas = new ArrayList<>();
        Elements cards = doc.select("div.job_seen_beacon");
        if (cards.isEmpty()) {
            cards = doc.select("div[data-jk]");
        }
        if (cards.isEmpty()) {
            cards = doc.select("td.resultContent");
        }
        logger.debug("Indeed - Cards HTML encontrados: {}", cards.size());

        for (Element card : cards) {
            try {
                Vaga vaga = extrairVagaDoCard(card, modalidadeDefault);
                if (vaga != null) {
                    vagas.add(vaga);
                }
            } catch (Exception e) {
                logger.debug("Indeed - Erro ao extrair card: {}", e.getMessage());
            }
        }
        return vagas;
    }

    private Vaga extrairVagaDoCard(Element card, String modalidadeDefault) {
        String titulo = primeiroTexto(card, "h2.jobTitle span[title]", "h2.jobTitle a span", "h2 a", "h2");
        if (titulo.isEmpty() || titulo.equalsIgnoreCase("new")) {
            return null;
        }
        String empresa = primeiroTexto(card, "span[data-testid=company-name]", ".companyName", "[class*=company]");
        String localizacao = primeiroTexto(card, "div[data-testid=text-location]", ".companyLocation", "[class*=location]");
        String dataTexto = primeiroTexto(card, "span[data-testid=myJobsStateDate]", ".date");
        String urlRelativa = primeiroHref(card, "h2.jobTitle a", "a[id^=job_]", "a[href*=/rc/clk]", "a");
        String urlVaga = absolutizarUrl(urlRelativa, "https://br.indeed.com");
        return montarVaga(titulo, empresa, localizacao, modalidadeDefault, dataTexto, urlVaga, "Indeed", "");
    }

    private List<Vaga> extrairVagasDoMosaic(String html, String modalidadeDefault) {
        List<Vaga> vagas = new ArrayList<>();
        Matcher matcher = MOSAIC_JSON.matcher(html);
        if (!matcher.find()) {
            return vagas;
        }
        try {
            JsonObject root = JsonParser.parseString(matcher.group(1)).getAsJsonObject();
            JsonArray results = encontrarArrayDeVagas(root);
            if (results == null) {
                return vagas;
            }
            for (JsonElement el : results) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject job = el.getAsJsonObject();
                String titulo = textoJson(job, "title", "displayTitle");
                String empresa = textoJson(job, "company", "companyName", "truncatedCompany");
                String localizacao = textoJson(job, "formattedLocation", "location");
                String dataTexto = textoJson(job, "formattedRelativeTime", "pubDate");
                String jobKey = textoJson(job, "jobkey", "jobKey", "jk");
                String urlVaga = jobKey.isEmpty()
                        ? "#"
                        : "https://br.indeed.com/viewjob?jk=" + jobKey;
                Vaga vaga = montarVaga(titulo, empresa, localizacao, modalidadeDefault, dataTexto, urlVaga, "Indeed", "");
                if (vaga != null) {
                    vagas.add(vaga);
                }
            }
        } catch (Exception e) {
            logger.debug("Indeed - Falha ao ler mosaic JSON: {}", e.getMessage());
        }
        return vagas;
    }

    private JsonArray encontrarArrayDeVagas(JsonObject root) {
        String[] caminhos = {"metaData.mosaicProviderJobCardsModel.results", "results"};
        for (String caminho : caminhos) {
            JsonElement atual = root;
            boolean ok = true;
            for (String parte : caminho.split("\\.")) {
                if (atual != null && atual.isJsonObject() && atual.getAsJsonObject().has(parte)) {
                    atual = atual.getAsJsonObject().get(parte);
                } else {
                    ok = false;
                    break;
                }
            }
            if (ok && atual != null && atual.isJsonArray()) {
                return atual.getAsJsonArray();
            }
        }
        return null;
    }

    private String textoJson(JsonObject obj, String... chaves) {
        for (String chave : chaves) {
            if (obj.has(chave) && !obj.get(chave).isJsonNull()) {
                String v = obj.get(chave).getAsString();
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
        }
        return "";
    }
}
