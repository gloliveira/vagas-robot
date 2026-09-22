package br.com.vagas.config;

import java.util.Arrays;
import java.util.List;

/**
 * Configuração central dos parâmetros de busca do robô.
 * Edite esta classe para personalizar os critérios de busca.
 */
public class ConfiguracaoBusca {

    // =========================================================
    //  PALAVRAS-CHAVE DE BUSCA
    // =========================================================
    public static final List<String> PALAVRAS_CHAVE = Arrays.asList(
            "desenvolvedor Java senior",
            "desenvolvedor Java pleno",
            "Java developer senior",
            "Java Spring Boot senior",
            "Java Spring Boot pleno",
            "Java JSF senior",
            "Java microservices senior"
    );

    // =========================================================
    //  LOCALIZAÇÃO
    // =========================================================
    /** Cidade de referência (não usada: a busca é só remoto). */
    public static final String CIDADE_PRESENCIAL = "Salvador";
    public static final String ESTADO_PRESENCIAL = "BA";
    /** País obrigatório das vagas (remoto no exterior é descartado). */
    public static final String PAIS = "Brasil";
    /** Apenas vagas 100% remotas (híbrido e presencial são ignorados). */
    public static final boolean SOMENTE_REMOTAS = true;

    // =========================================================
    //  FILTRO DE TEMPO
    // =========================================================
    /** Máximo de dias desde a publicação (14 = 2 semanas) */
    public static final int MAX_DIAS_PUBLICACAO = 2;

    // =========================================================
    //  AGENDAMENTO
    // =========================================================
    /** Intervalo de execução automática em horas */
    public static final int INTERVALO_HORAS = 6;

    // =========================================================
    //  RELATÓRIO
    // =========================================================
    /** Diretório onde os relatórios HTML serão salvos */
    public static final String DIRETORIO_RELATORIO = "relatorios";

    // =========================================================
    //  TECNOLOGIAS RELEVANTES (para destacar no relatório)
    // =========================================================
    public static final List<String> TECNOLOGIAS_RELEVANTES = Arrays.asList(
            "Java", "Spring Boot", "Spring", "JSF", "Microservices", "Microserviços",
            "REST", "API", "JPA", "Hibernate", "Maven", "Gradle", "Docker",
            "Kubernetes", "AWS", "Azure", "GCP", "SQL", "PostgreSQL", "MySQL",
            "Oracle", "MongoDB", "Redis", "Kafka", "RabbitMQ", "Git", "CI/CD",
            "JUnit", "Mockito", "Swagger", "OpenAPI", "PrimeFaces"
    );

    // =========================================================
    //  CONFIGURAÇÕES HTTP
    // =========================================================
    public static final int TIMEOUT_CONEXAO_MS = 15000;
    public static final int TIMEOUT_LEITURA_MS = 20000;
    /** Delay entre requisições para evitar bloqueio (ms) */
    public static final int DELAY_ENTRE_REQUISICOES_MS = 1500;

    // =========================================================
    //  USER AGENTS (rotação para evitar bloqueio)
    // =========================================================
    public static final List<String> USER_AGENTS = Arrays.asList(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:129.0) Gecko/20100101 Firefox/129.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Safari/605.1.15"
    );

    private ConfiguracaoBusca() {}
}
