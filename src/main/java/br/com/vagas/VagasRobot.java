package br.com.vagas;

import br.com.vagas.config.ConfiguracaoBusca;
import br.com.vagas.scheduler.AgendadorBusca;
import br.com.vagas.util.SslAmbiente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

/**
 * Classe principal do Robô de Vagas Java.
 *
 * Busca vagas de desenvolvedor Java Sênior/Pleno nas plataformas:
 *   - LinkedIn (guest API pública)
 *   - Gupy
 *   - Vagas.com.br
 *   - Programathor
 *   - Catho
 *   - InfoJobs Brasil
 *   - Indeed Brasil (quando o Cloudflare permitir)
 *
 * Critérios de busca:
 *   - Somente vagas remotas no Brasil
 *   - Publicadas há no máximo 14 dias (2 semanas)
 *   - Tecnologias: Java, Spring Boot, JSF, Microserviços
 *
 * Uso:
 *   java -jar vagas-robot.jar           → Menu interativo
 *   java -jar vagas-robot.jar --auto    → Modo agendado (a cada 6h)
 *   java -jar vagas-robot.jar --now     → Execução única imediata
 */
public class VagasRobot {

    private static final Logger logger = LoggerFactory.getLogger(VagasRobot.class);

    public static void main(String[] args) {
        SslAmbiente.garantirTrustStorePadrao();
        imprimirBanner();

        AgendadorBusca agendador = new AgendadorBusca();

        // Verificar argumentos de linha de comando
        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "--auto":
                case "-a":
                    logger.info("Modo: Agendamento automático a cada {} horas", ConfiguracaoBusca.INTERVALO_HORAS);
                    agendador.iniciarAgendamento();
                    aguardarIndefinidamente(agendador);
                    return;

                case "--now":
                case "-n":
                    logger.info("Modo: Execução única imediata");
                    agendador.executarManualmente();
                    return;

                case "--help":
                case "-h":
                    imprimirAjuda();
                    return;

                default:
                    logger.warn("Argumento desconhecido: {}. Use --help para ver as opções.", args[0]);
            }
        }

        // Menu interativo padrão
        executarMenuInterativo(agendador);
    }

    private static void executarMenuInterativo(AgendadorBusca agendador) {
        Scanner scanner = new Scanner(System.in);
        boolean executando = true;

        while (executando) {
            System.out.println("\n╔══════════════════════════════════════════╗");
            System.out.println("║       ROBÔ DE VAGAS JAVA - MENU          ║");
            System.out.println("╠══════════════════════════════════════════╣");
            System.out.println("║  [1] Buscar vagas AGORA (execução única) ║");
            System.out.println("║  [2] Iniciar agendamento automático      ║");
            System.out.println("║      (a cada " + ConfiguracaoBusca.INTERVALO_HORAS + " horas)                   ║");
            System.out.println("║  [3] Ver configurações atuais            ║");
            System.out.println("║  [0] Sair                                ║");
            System.out.println("╚══════════════════════════════════════════╝");
            System.out.print("  Escolha uma opção: ");

            String opcao = scanner.nextLine().trim();

            switch (opcao) {
                case "1":
                    System.out.println("\n🔍 Iniciando busca de vagas...");
                    agendador.executarManualmente();
                    System.out.println("\n✅ Busca concluída! Verifique a pasta '" +
                            ConfiguracaoBusca.DIRETORIO_RELATORIO + "' para o relatório HTML.");
                    break;

                case "2":
                    System.out.println("\n⏰ Iniciando agendamento automático a cada " +
                            ConfiguracaoBusca.INTERVALO_HORAS + " horas...");
                    System.out.println("   (A primeira busca será executada agora)");
                    System.out.println("   Pressione ENTER para parar o agendamento.\n");
                    agendador.iniciarAgendamento();
                    scanner.nextLine(); // Aguarda ENTER para parar
                    agendador.parar();
                    System.out.println("⏹  Agendamento pausado.");
                    break;

                case "3":
                    imprimirConfiguracoes();
                    break;

                case "0":
                    System.out.println("\n👋 Encerrando o Robô de Vagas. Até logo!");
                    agendador.parar();
                    executando = false;
                    break;

                default:
                    System.out.println("⚠️  Opção inválida. Tente novamente.");
            }
        }

        scanner.close();
    }

    private static void aguardarIndefinidamente(AgendadorBusca agendador) {
        System.out.println("Agendamento ativo. Pressione Ctrl+C para encerrar.");
        // Hook para encerramento gracioso
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Sinal de encerramento recebido. Parando agendador...");
            agendador.parar();
        }));
        // Manter a thread principal viva
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void imprimirBanner() {
        System.out.println();
        System.out.println("  ██████╗  ██████╗ ██████╗  ██████╗     ██╗ █████╗ ██╗   ██╗ █████╗ ");
        System.out.println("  ██╔══██╗██╔═══██╗██╔══██╗██╔═══██╗    ██║██╔══██╗██║   ██║██╔══██╗");
        System.out.println("  ██████╔╝██║   ██║██████╔╝██║   ██║    ██║███████║██║   ██║███████║");
        System.out.println("  ██╔══██╗██║   ██║██╔══██╗██║   ██║██  ██║██╔══██║╚██╗ ██╔╝██╔══██║");
        System.out.println("  ██║  ██║╚██████╔╝██████╔╝╚██████╔╝╚████╔╝██║  ██║ ╚████╔╝ ██║  ██║");
        System.out.println("  ╚═╝  ╚═╝ ╚═════╝ ╚═════╝  ╚═════╝  ╚═══╝ ╚═╝  ╚═╝  ╚═══╝  ╚═╝  ╚═╝");
        System.out.println();
        System.out.println("  ☕ Robô de Vagas Java Sênior/Pleno v1.1.0");
        System.out.println("  LinkedIn | Gupy | Vagas.com | Programathor | Catho | InfoJobs | Indeed");
        System.out.println("  Somente vagas remotas no Brasil");
        System.out.println();
    }

    private static void imprimirAjuda() {
        System.out.println("Uso: java -jar vagas-robot.jar [opção]");
        System.out.println();
        System.out.println("Opções:");
        System.out.println("  (sem argumento)    Menu interativo");
        System.out.println("  --now, -n          Executa busca uma única vez e encerra");
        System.out.println("  --auto, -a         Executa em modo agendado (a cada " +
                ConfiguracaoBusca.INTERVALO_HORAS + "h)");
        System.out.println("  --help, -h         Exibe esta ajuda");
        System.out.println();
        System.out.println("Relatórios gerados em: ./" + ConfiguracaoBusca.DIRETORIO_RELATORIO + "/");
    }

    private static void imprimirConfiguracoes() {
        System.out.println("\n📋 CONFIGURAÇÕES ATUAIS:");
        System.out.println("  Plataformas: LinkedIn, Gupy, Vagas.com, Programathor, Catho, InfoJobs, Indeed");
        System.out.println("  País: somente Brasil (vagas no exterior são ignoradas)");
        System.out.println("  Modalidade: somente remoto (presencial e híbrido são ignorados)");
        System.out.println("  Máximo de dias desde publicação: " + ConfiguracaoBusca.MAX_DIAS_PUBLICACAO);
        System.out.println("  Intervalo de agendamento: " + ConfiguracaoBusca.INTERVALO_HORAS + " horas");
        System.out.println("  Diretório de relatórios: " + ConfiguracaoBusca.DIRETORIO_RELATORIO);
        System.out.println("  Tecnologias buscadas: Java, Spring Boot, JSF, Microserviços");
        System.out.println();
        System.out.println("  Para alterar as configurações, edite:");
        System.out.println("  src/main/java/br/com/vagas/config/ConfiguracaoBusca.java");
    }
}
