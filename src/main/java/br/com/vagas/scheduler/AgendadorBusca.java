package br.com.vagas.scheduler;

import br.com.vagas.config.ConfiguracaoBusca;
import br.com.vagas.model.ResultadoBusca;
import br.com.vagas.model.Vaga;
import br.com.vagas.report.GeradorRelatorioHTML;
import br.com.vagas.scraper.*;
import br.com.vagas.util.SslAmbiente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Agendador que executa a busca de vagas automaticamente em intervalos configurados.
 * Também permite execução manual sob demanda.
 */
public class AgendadorBusca {

    private static final Logger logger = LoggerFactory.getLogger(AgendadorBusca.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final ScheduledExecutorService executor;
    private final GeradorRelatorioHTML geradorRelatorio;
    private final List<ScraperVagas> scrapers;

    public AgendadorBusca() {
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agendador-vagas");
            t.setDaemon(true);
            return t;
        });
        this.geradorRelatorio = new GeradorRelatorioHTML();
        this.scrapers = inicializarScrapers();
    }

    private List<ScraperVagas> inicializarScrapers() {
        List<ScraperVagas> lista = new ArrayList<>();
        lista.add(new LinkedInScraper());
        lista.add(new GupyScraper());
        lista.add(new VagasComScraper());
        lista.add(new ProgramathorScraper());
        lista.add(new CathoScraper());
        lista.add(new InfoJobsScraper());
        lista.add(new IndeedScraper());
        return lista;
    }

    /**
     * Inicia o agendamento automático com o intervalo configurado.
     * Executa imediatamente na primeira vez e depois a cada N horas.
     */
    public void iniciarAgendamento() {
        int intervaloHoras = ConfiguracaoBusca.INTERVALO_HORAS;
        logger.info("Agendamento iniciado. Intervalo: {} horas", intervaloHoras);
        logger.info("Próxima execução automática em {} horas", intervaloHoras);

        // Executa imediatamente e depois a cada N horas
        executor.scheduleAtFixedRate(
                this::executarBusca,
                0,                    // Delay inicial: 0 (executa imediatamente)
                intervaloHoras,       // Período
                TimeUnit.HOURS
        );
    }

    /**
     * Executa a busca de vagas manualmente (sem aguardar o agendamento).
     */
    public void executarManualmente() {
        logger.info("Execução manual iniciada pelo usuário");
        executarBusca();
    }

    /**
     * Para o agendador e libera os recursos.
     */
    public void parar() {
        logger.info("Parando agendador...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Agendador parado.");
    }

    /**
     * Executa a busca em todas as plataformas e gera o relatório HTML.
     */
    private void executarBusca() {
        SslAmbiente.garantirTrustStorePadrao();
        String inicio = LocalDateTime.now().format(FORMATTER);
        logger.info("========================================");
        logger.info("Iniciando busca de vagas - {}", inicio);
        logger.info("========================================");

        List<Vaga> todasVagas = new ArrayList<>();
        List<ResultadoBusca> resultados = new ArrayList<>();

        for (ScraperVagas scraper : scrapers) {
            try {
                logger.info("Executando scraper: {}", scraper.getNomePlataforma());
                ResultadoBusca resultado = scraper.buscarVagas();
                resultados.add(resultado);
                todasVagas.addAll(resultado.getVagas());
                logger.info("{}: status={} | {} | vagas={}",
                        resultado.getPlataforma(),
                        resultado.getStatus(),
                        resultado.getDetalhe(),
                        resultado.getVagas().size());
            } catch (Exception e) {
                logger.error("ERRO DE CONEXÃO/execução no scraper {}: {}", scraper.getNomePlataforma(), e.getMessage(), e);
                resultados.add(new ResultadoBusca(
                        scraper.getNomePlataforma(),
                        ResultadoBusca.Status.ERRO_CONEXAO,
                        List.of(),
                        e.getMessage()));
            }
        }

        logger.info("Total de vagas coletadas (com possíveis duplicatas): {}", todasVagas.size());

        try {
            Path relatorio = geradorRelatorio.gerarRelatorio(todasVagas, resultados, ConfiguracaoBusca.DIRETORIO_RELATORIO);
            logger.info("========================================");
            logger.info("Relatório gerado com sucesso!");
            logger.info("Arquivo: {}", relatorio.toAbsolutePath());
            logger.info("Total de vagas no relatório: {}", todasVagas.size());
            for (ResultadoBusca r : resultados) {
                logger.info("  {} → {} ({})", r.getPlataforma(), r.getStatus(), r.getDetalhe());
            }
            logger.info("========================================");

            abrirRelatorioNoBrowser(relatorio);

        } catch (IOException e) {
            logger.error("Erro ao gerar relatório: {}", e.getMessage(), e);
        }
    }

    private void abrirRelatorioNoBrowser(Path relatorio) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String[] cmd;
            if (os.contains("win")) {
                cmd = new String[]{"cmd", "/c", "start", relatorio.toAbsolutePath().toString()};
            } else if (os.contains("mac")) {
                cmd = new String[]{"open", relatorio.toAbsolutePath().toString()};
            } else {
                cmd = new String[]{"xdg-open", relatorio.toAbsolutePath().toString()};
            }
            Runtime.getRuntime().exec(cmd);
            logger.info("Relatório aberto no navegador padrão.");
        } catch (IOException e) {
            logger.debug("Não foi possível abrir o navegador automaticamente: {}", e.getMessage());
            logger.info("Abra manualmente o arquivo: {}", relatorio.toAbsolutePath());
        }
    }
}
