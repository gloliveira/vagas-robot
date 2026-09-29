package br.com.vagas.diagnostico;

import br.com.vagas.util.SslAmbiente;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Diagnóstico isolado de HTTPS/TLS com o mesmo JRE da aplicação.
 * Uso: java br.com.vagas.diagnostico.DiagnosticoHttps
 */
public class DiagnosticoHttps {

    public static void main(String[] args) {
        SslAmbiente.garantirTrustStorePadrao();
        imprimirAmbiente();
        testarSslContext();
        testarKeystore("JKS");
        testarKeystore("PKCS12");
        testarKeystore("Windows-ROOT");
        testarHttps("https://www.google.com");
        testarHttps("https://www.linkedin.com");
        testarHttps("https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search?keywords=java&location=Brasil&start=0");
        testarHttps("https://employability-portal.gupy.io");
        testarHttps("https://www.vagas.com.br");
        testarHttps("https://programathor.com.br");
        testarHttps("https://www.catho.com.br");
        testarHttps("https://www.infojobs.com.br");
        testarHttps("https://br.indeed.com");
        testarHttps("https://www.glassdoor.com.br");
        imprimirCadeia("www.google.com", 443);
        imprimirCadeia("www.linkedin.com", 443);
    }

    public static void imprimirAmbiente() {
        System.out.println("=== JVM ===");
        System.out.println("java.version              = " + System.getProperty("java.version"));
        System.out.println("java.home                 = " + System.getProperty("java.home"));
        System.out.println("java.vendor               = " + System.getProperty("java.vendor"));
        System.out.println("os.name                   = " + System.getProperty("os.name"));
        System.out.println("javax.net.ssl.trustStore     = " + System.getProperty("javax.net.ssl.trustStore"));
        System.out.println("javax.net.ssl.trustStoreType = " + System.getProperty("javax.net.ssl.trustStoreType"));
        System.out.println("javax.net.ssl.keyStore       = " + System.getProperty("javax.net.ssl.keyStore"));
        System.out.println("https.protocols              = " + System.getProperty("https.protocols"));
        System.out.println("jdk.tls.client.protocols     = " + System.getProperty("jdk.tls.client.protocols"));
        System.out.println("JAVA_HOME           = " + System.getenv("JAVA_HOME"));
        System.out.println("JAVA_TOOL_OPTIONS   = " + System.getenv("JAVA_TOOL_OPTIONS"));
        System.out.println("JDK_JAVA_OPTIONS    = " + System.getenv("JDK_JAVA_OPTIONS"));
        System.out.println("_JAVA_OPTIONS       = " + System.getenv("_JAVA_OPTIONS"));
    }

    private static void testarSslContext() {
        System.out.println("=== SSLContext.getDefault() ===");
        try {
            SSLContext ctx = SSLContext.getDefault();
            System.out.println("OK protocol=" + ctx.getProtocol() + " provider=" + ctx.getProvider());
        } catch (Exception e) {
            System.out.println("FALHOU: " + e);
            imprimirCausas(e);
        }
    }

    private static void testarKeystore(String tipo) {
        System.out.println("=== KeyStore.getInstance(\"" + tipo + "\") ===");
        try {
            KeyStore ks = KeyStore.getInstance(tipo);
            if ("Windows-ROOT".equalsIgnoreCase(tipo)) {
                ks.load(null, null);
            }
            System.out.println("OK type=" + ks.getType() + " provider=" + ks.getProvider()
                    + " size=" + (ks.size() >= 0 ? ks.size() : "?"));
        } catch (Exception e) {
            System.out.println("FALHOU: " + e);
            imprimirCausas(e);
        }
    }

    private static void testarHttps(String url) {
        System.out.println("=== HTTPS " + url + " ===");
        HttpsURLConnection conn = null;
        try {
            conn = (HttpsURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "vagas-robot-diagnostico");
            int status = conn.getResponseCode();
            String cipher = conn.getCipherSuite();
            System.out.println("HTTP " + status + " cipher=" + cipher);
        } catch (Exception e) {
            System.out.println("FALHOU: " + e);
            imprimirCausas(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Mostra a cadeia apresentada pelo servidor e em seguida valida no cacerts padrão.
     * Não desativa HTTPS e não usa trust-all.
     */
    private static void imprimirCadeia(String host, int porta) {
        System.out.println("=== Cadeia TLS " + host + ":" + porta + " ===");
        final X509Certificate[][] capturada = new X509Certificate[1][];
        try {
            X509TrustManager padrao = trustManagerPadrao();
            SSLContext ctx = SSLContext.getInstance("TLS");
            X509TrustManager captura = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    padrao.checkClientTrusted(chain, authType);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    capturada[0] = chain;
                    padrao.checkServerTrusted(chain, authType);
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return padrao.getAcceptedIssuers();
                }
            };
            ctx.init(null, new TrustManager[]{captura}, null);
            SSLSocketFactory factory = ctx.getSocketFactory();
            try (SSLSocket socket = (SSLSocket) factory.createSocket(host, porta)) {
                socket.setSoTimeout(15000);
                socket.startHandshake();
                System.out.println("Handshake OK cipher=" + socket.getSession().getCipherSuite());
            }
            imprimirCerts(capturada[0]);
        } catch (Exception e) {
            System.out.println("FALHOU: " + e);
            imprimirCausas(e);
            imprimirCerts(capturada[0]);
        }
    }

    private static void imprimirCerts(X509Certificate[] chain) {
        if (chain == null) {
            System.out.println("Nenhuma cadeia capturada.");
            return;
        }
        for (int i = 0; i < chain.length; i++) {
            X509Certificate c = chain[i];
            System.out.println("  [" + i + "] subject=" + c.getSubjectX500Principal());
            System.out.println("       issuer =" + c.getIssuerX500Principal());
            System.out.println("       notAfter=" + c.getNotAfter());
        }
    }

    private static X509TrustManager trustManagerPadrao() throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        throw new IllegalStateException("X509TrustManager padrão não encontrado");
    }

    private static void imprimirCausas(Throwable e) {
        int i = 0;
        while (e != null && i < 8) {
            System.out.println("  cause[" + i + "] " + e.getClass().getName() + ": " + e.getMessage());
            e = e.getCause();
            i++;
        }
    }
}
