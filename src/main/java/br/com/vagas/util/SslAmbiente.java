package br.com.vagas.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

/**
 * Prepara o truststore HTTPS sem desativar TLS e sem trust-all.
 *
 * Neste ambiente o Check Point Harmony SASE intercepta HTTPS. A CA está no
 * repositório Windows, mas não no cacerts do JRE.
 *
 * JDKs 17+ (Oracle/OpenJDK completo) têm SunMSCAPI e podem usar
 * {@code Windows-ROOT}. O OpenLogic JRE 11 não tem esse provider: apontar
 * Windows-ROOT nele derruba o {@code SSLContext} inteiro.
 */
public final class SslAmbiente {

    private static final Logger logger = LoggerFactory.getLogger(SslAmbiente.class);
    private static final String ALIAS_SASE = "checkpoint-harmony-sase";
    private static final String RECURSO_CA = "/ssl/checkpoint-harmony-sase.cer";
    private static final char[] SENHA_CACERTS = "changeit".toCharArray();

    private static boolean inicializado = false;

    public static synchronized void garantirTrustStorePadrao() {
        if (inicializado) {
            return;
        }
        inicializado = true;

        if (windowsRootDisponivel()) {
            System.clearProperty("javax.net.ssl.trustStore");
            System.clearProperty("javax.net.ssl.trustStorePassword");
            System.setProperty("javax.net.ssl.trustStoreType", "Windows-ROOT");
            logger.info("HTTPS usando Windows-ROOT (SunMSCAPI). Inclui a CA do Check Point Harmony SASE.");
        } else {
            String tipo = System.getProperty("javax.net.ssl.trustStoreType");
            if (tipo != null && "Windows-ROOT".equalsIgnoreCase(tipo.trim())) {
                System.clearProperty("javax.net.ssl.trustStoreType");
                logger.warn("Removido javax.net.ssl.trustStoreType=Windows-ROOT. "
                        + "Este JRE não oferece o KeyStore Windows-ROOT.");
            }
            try {
                instalarTrustStoreComCaSase();
            } catch (Exception e) {
                logger.warn("Não foi possível acrescentar a CA do Check Point Harmony SASE ao truststore: {}",
                        e.toString());
            }
        }

        logger.info("JVM java.home={} java.version={}",
                System.getProperty("java.home"), System.getProperty("java.version"));
        logger.info("SSL trustStore={} trustStoreType={} keyStore={}",
                valor(System.getProperty("javax.net.ssl.trustStore")),
                valor(System.getProperty("javax.net.ssl.trustStoreType")),
                valor(System.getProperty("javax.net.ssl.keyStore")));
        logger.info("Env JAVA_TOOL_OPTIONS={} JDK_JAVA_OPTIONS={} _JAVA_OPTIONS={}",
                valor(System.getenv("JAVA_TOOL_OPTIONS")),
                valor(System.getenv("JDK_JAVA_OPTIONS")),
                valor(System.getenv("_JAVA_OPTIONS")));
    }

    private static boolean windowsRootDisponivel() {
        try {
            KeyStore ks = KeyStore.getInstance("Windows-ROOT");
            ks.load(null, null);
            return ks.size() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void instalarTrustStoreComCaSase() throws Exception {
        Certificate caSase = carregarCaSase();
        if (caSase == null) {
            logger.info("CA Check Point Harmony SASE não encontrada no classpath; usando cacerts padrão.");
            return;
        }

        Path cacerts = Paths.get(System.getProperty("java.home"), "lib", "security", "cacerts");
        if (!Files.isRegularFile(cacerts)) {
            logger.warn("cacerts não encontrado em {}", cacerts);
            return;
        }

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        try (InputStream in = Files.newInputStream(cacerts)) {
            ks.load(in, SENHA_CACERTS);
        }

        ks.setCertificateEntry(ALIAS_SASE, caSase);

        Path temporario = Files.createTempFile("vagas-robot-truststore-", ".jks");
        temporario.toFile().deleteOnExit();
        try (OutputStream out = Files.newOutputStream(temporario)) {
            ks.store(out, SENHA_CACERTS);
        }

        System.setProperty("javax.net.ssl.trustStore", temporario.toAbsolutePath().toString());
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        System.setProperty("javax.net.ssl.trustStoreType", ks.getType());
        logger.info("Truststore padrão do JRE acrescido da CA Check Point Harmony SASE (não é trust-all).");
    }

    private static Certificate carregarCaSase() throws Exception {
        try (InputStream in = SslAmbiente.class.getResourceAsStream(RECURSO_CA)) {
            if (in != null) {
                return CertificateFactory.getInstance("X.509").generateCertificate(in);
            }
        }
        Path local = Paths.get("src", "main", "resources", "ssl", "checkpoint-harmony-sase.cer");
        if (Files.isRegularFile(local)) {
            try (InputStream in = Files.newInputStream(local)) {
                return CertificateFactory.getInstance("X.509").generateCertificate(in);
            }
        }
        return null;
    }

    private static String valor(String s) {
        return (s == null || s.isBlank()) ? "(não definido)" : s;
    }

    private SslAmbiente() {}
}
