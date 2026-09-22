package br.com.vagas.diagnostico;

import br.com.vagas.util.HttpUtil;
import br.com.vagas.util.SslAmbiente;
import org.jsoup.nodes.Document;

/**
 * Confirma que o HttpUtil consegue HTTPS após o truststore padrão.
 */
public class DiagnosticoHttpUtil {

    public static void main(String[] args) {
        SslAmbiente.garantirTrustStorePadrao();
        testar("https://www.google.com");
        testar("https://www.linkedin.com");
    }

    private static void testar(String url) {
        System.out.println("=== HttpUtil GET " + url + " ===");
        try {
            Document doc = HttpUtil.get(url);
            System.out.println("OK parse() title='" + doc.title() + "' chars=" + doc.html().length());
        } catch (Exception e) {
            System.out.println("FALHOU: " + e);
            e.printStackTrace(System.out);
        }
    }
}
