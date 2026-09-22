package br.com.vagas.util;

import br.com.vagas.config.ConfiguracaoBusca;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * Requisições HTTP(S) com o truststore preparado por {@link SslAmbiente}.
 */
public class HttpUtil {

    private static final Logger logger = LoggerFactory.getLogger(HttpUtil.class);
    private static final Random random = new Random();

    static {
        SslAmbiente.garantirTrustStorePadrao();
    }

    public static String getUserAgentAleatorio() {
        List<String> agents = ConfiguracaoBusca.USER_AGENTS;
        return agents.get(random.nextInt(agents.size()));
    }

    public static Document get(String url) throws IOException {
        return get(url, null);
    }

    public static Document get(String url, String referer) throws IOException {
        logger.debug("GET: {}", url);
        aguardarDelay();
        try {
            Connection.Response response = novaConexao(url, referer)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .ignoreHttpErrors(true)
                    .execute();
            Document doc = response.parse();
            validarResposta(url, response.statusCode(), doc, null);
            return doc;
        } catch (FalhaHttpException e) {
            throw e;
        } catch (IOException e) {
            throw classificarConexao(url, e);
        }
    }

    public static String getJson(String url) throws IOException {
        return getJson(url, null);
    }

    public static String getJson(String url, String referer) throws IOException {
        logger.debug("GET JSON: {}", url);
        aguardarDelay();
        try {
            Connection.Response response = novaConexao(url, referer)
                    .header("Accept", "application/json, text/plain, */*")
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .execute();
            String body = response.body();
            if (body == null) {
                body = "";
            }
            validarResposta(url, response.statusCode(), null, body);
            return body;
        } catch (FalhaHttpException e) {
            throw e;
        } catch (IOException e) {
            throw classificarConexao(url, e);
        }
    }

    public static String getString(String url) throws IOException {
        return getJson(url, null);
    }

    public static boolean isPaginaDeBloqueio(Document doc, String body) {
        String titulo = doc != null ? doc.title() : "";
        String texto = body != null ? body : (doc != null ? doc.text() : "");
        String combinado = (titulo + " " + texto).toLowerCase();
        return combinado.contains("cf-mitigated")
                || combinado.contains("just a moment")
                || combinado.contains("attention required")
                || combinado.contains("enable javascript and cookies to continue")
                || combinado.contains("security check - indeed")
                || combinado.contains("idn-challenge")
                || titulo.toLowerCase().contains("security check");
    }

    private static void validarResposta(String url, int status, Document doc, String body) throws FalhaHttpException {
        if (isPaginaDeBloqueio(doc, body)) {
            throw new FalhaHttpException(FalhaHttpException.Tipo.BLOQUEIO,
                    "BLOQUEIO ANTI-BOT em " + url + " (Cloudflare/CAPTCHA, HTTP " + status + ")",
                    status);
        }
        if (status >= 400) {
            throw new FalhaHttpException(FalhaHttpException.Tipo.HTTP_INESPERADO,
                    "HTTP " + status + " em " + url,
                    status);
        }
    }

    private static FalhaHttpException classificarConexao(String url, IOException e) {
        Throwable causa = e;
        while (causa.getCause() != null && causa.getCause() != causa) {
            causa = causa.getCause();
        }
        String detalhe = causa.getClass().getSimpleName() + ": " + causa.getMessage();
        return new FalhaHttpException(FalhaHttpException.Tipo.ERRO_CONEXAO,
                "ERRO DE CONEXÃO em " + url + " (" + detalhe + ")",
                -1, e);
    }

    private static Connection novaConexao(String url, String referer) {
        Connection connection = Jsoup.connect(url)
                .userAgent(getUserAgentAleatorio())
                .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", referer != null ? "same-origin" : "none")
                .header("Upgrade-Insecure-Requests", "1")
                .timeout(ConfiguracaoBusca.TIMEOUT_LEITURA_MS)
                .followRedirects(true)
                .maxBodySize(0);
        if (referer != null && !referer.isBlank()) {
            connection.referrer(referer);
        }
        return connection;
    }

    private static void aguardarDelay() {
        try {
            int delay = ConfiguracaoBusca.DELAY_ENTRE_REQUISICOES_MS
                    + random.nextInt(1000);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private HttpUtil() {}
}
