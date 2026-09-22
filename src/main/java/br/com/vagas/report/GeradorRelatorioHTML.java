package br.com.vagas.report;

import br.com.vagas.config.ConfiguracaoBusca;
import br.com.vagas.model.ResultadoBusca;
import br.com.vagas.model.Vaga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gera relatório HTML com as vagas encontradas pelo robô.
 * O relatório inclui:
 * - Resumo estatístico
 * - Filtros por plataforma e modalidade
 * - Cards de vagas com links clicáveis
 * - Design responsivo e profissional
 */
public class GeradorRelatorioHTML {

    private static final Logger logger = LoggerFactory.getLogger(GeradorRelatorioHTML.class);
    private static final DateTimeFormatter FORMATTER_ARQUIVO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
    private static final DateTimeFormatter FORMATTER_EXIBICAO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Gera o relatório HTML e salva no diretório especificado.
     *
     * @param vagas     Lista de vagas encontradas
     * @param diretorio Diretório onde o arquivo será salvo
     * @return Caminho do arquivo gerado
     */
    public Path gerarRelatorio(List<Vaga> vagas, String diretorio) throws IOException {
        return gerarRelatorio(vagas, java.util.Collections.emptyList(), diretorio);
    }

    public Path gerarRelatorio(List<Vaga> vagas, List<ResultadoBusca> resultados, String diretorio) throws IOException {
        Path dir = Paths.get(diretorio);
        Files.createDirectories(dir);

        String nomeArquivo = "vagas-java-" + LocalDateTime.now().format(FORMATTER_ARQUIVO) + ".html";
        Path caminhoArquivo = dir.resolve(nomeArquivo);

        List<Vaga> vagasSemDuplicatas = removerDuplicatas(vagas);
        vagasSemDuplicatas.sort(Comparator.comparing(
                v -> v.getDataPublicacaoDate() != null ? v.getDataPublicacaoDate() : java.time.LocalDate.MIN,
                Comparator.reverseOrder()
        ));

        String html = gerarHTML(vagasSemDuplicatas, resultados == null ? java.util.Collections.emptyList() : resultados);
        Files.write(caminhoArquivo, html.getBytes(StandardCharsets.UTF_8));
        logger.info("Relatório gerado: {}", caminhoArquivo.toAbsolutePath());
        return caminhoArquivo;
    }

    private List<Vaga> removerDuplicatas(List<Vaga> vagas) {
        Map<String, Vaga> mapa = new LinkedHashMap<>();
        for (Vaga vaga : vagas) {
            String chave = vaga.getTitulo() + "|" + vaga.getEmpresa();
            mapa.putIfAbsent(chave, vaga);
        }
        return new java.util.ArrayList<>(mapa.values());
    }

    private String gerarHTML(List<Vaga> vagas, List<ResultadoBusca> resultados) {
        long totalSenior = vagas.stream().filter(v -> "Sênior".equals(v.getNivel())).count();
        long totalPleno = vagas.stream().filter(v -> "Pleno".equals(v.getNivel())).count();
        long totalRemoto = vagas.stream().filter(v -> "Remoto".equals(v.getModalidade())).count();
        long totalHibrido = vagas.stream().filter(v -> "Híbrido".equals(v.getModalidade())).count();
        long totalPresencial = vagas.stream().filter(v -> "Presencial".equals(v.getModalidade()) ||
                "Presencial/Híbrido".equals(v.getModalidade())).count();

        Map<String, Long> porPlataforma = vagas.stream()
                .collect(Collectors.groupingBy(Vaga::getPlataforma, Collectors.counting()));

        StringBuilder sb = new StringBuilder();
        sb.append(gerarCabecalhoHTML());
        sb.append(gerarSecaoResumo(vagas.size(), totalSenior, totalPleno, totalRemoto, totalHibrido, totalPresencial, porPlataforma));
        sb.append(gerarSecaoStatusFontes(resultados));
        sb.append(gerarSecaoVagas(vagas));
        sb.append(gerarRodapeHTML());

        return sb.toString();
    }

    private String gerarSecaoStatusFontes(List<ResultadoBusca> resultados) {
        if (resultados == null || resultados.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("    <section class=\"status-fontes\">\n");
        sb.append("      <h2 style=\"font-size:16px;margin:0 0 12px 0;\">Status por plataforma</h2>\n");
        sb.append("      <table class=\"tabela-status\">\n");
        sb.append("        <tr><th>Fonte</th><th>Status</th><th>Vagas</th><th>Detalhe</th></tr>\n");
        for (ResultadoBusca r : resultados) {
            sb.append("        <tr>");
            sb.append("<td>").append(escapeHtml(r.getPlataforma())).append("</td>");
            sb.append("<td>").append(escapeHtml(r.getStatus().name())).append("</td>");
            sb.append("<td>").append(r.getVagas().size()).append("</td>");
            sb.append("<td>").append(escapeHtml(r.getDetalhe())).append("</td>");
            sb.append("</tr>\n");
        }
        sb.append("      </table>\n");
        sb.append("    </section>\n");
        return sb.toString();
    }

    private String gerarCabecalhoHTML() {
        return "<!DOCTYPE html>\n" +
               "<html lang=\"pt-BR\">\n" +
               "<head>\n" +
               "  <meta charset=\"UTF-8\">\n" +
               "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
               "  <title>Vagas Java Sênior/Pleno - " + LocalDateTime.now().format(FORMATTER_EXIBICAO) + "</title>\n" +
               "  <style>\n" +
               getCSSEstilo() +
               "  </style>\n" +
               "</head>\n" +
               "<body>\n" +
               "  <header class=\"header\">\n" +
               "    <div class=\"header-content\">\n" +
               "      <div class=\"header-icon\">☕</div>\n" +
               "      <div>\n" +
               "        <h1>Vagas Java Sênior / Pleno</h1>\n" +
               "        <p class=\"header-sub\">Gerado em " + LocalDateTime.now().format(FORMATTER_EXIBICAO) +
               " &nbsp;|&nbsp; Vagas dos últimos " + ConfiguracaoBusca.MAX_DIAS_PUBLICACAO +
               " dias &nbsp;|&nbsp; Somente vagas remotas no Brasil</p>\n" +
               "      </div>\n" +
               "    </div>\n" +
               "  </header>\n" +
               "  <main class=\"container\">\n";
    }

    private String gerarSecaoResumo(int total, long senior, long pleno, long remoto,
                                     long hibrido, long presencial,
                                     Map<String, Long> porPlataforma) {
        StringBuilder sb = new StringBuilder();
        sb.append("    <section class=\"resumo\">\n");
        sb.append("      <div class=\"stats-grid\">\n");
        sb.append("        <div class=\"stat-card stat-total\">\n");
        sb.append("          <span class=\"stat-num\">").append(total).append("</span>\n");
        sb.append("          <span class=\"stat-label\">Total de Vagas</span>\n");
        sb.append("        </div>\n");
        sb.append("        <div class=\"stat-card stat-senior\">\n");
        sb.append("          <span class=\"stat-num\">").append(senior).append("</span>\n");
        sb.append("          <span class=\"stat-label\">Sênior</span>\n");
        sb.append("        </div>\n");
        sb.append("        <div class=\"stat-card stat-pleno\">\n");
        sb.append("          <span class=\"stat-num\">").append(pleno).append("</span>\n");
        sb.append("          <span class=\"stat-label\">Pleno</span>\n");
        sb.append("        </div>\n");
        sb.append("        <div class=\"stat-card stat-remoto\">\n");
        sb.append("          <span class=\"stat-num\">").append(remoto).append("</span>\n");
        sb.append("          <span class=\"stat-label\">Remoto</span>\n");
        sb.append("        </div>\n");
        sb.append("        <div class=\"stat-card stat-hibrido\">\n");
        sb.append("          <span class=\"stat-num\">").append(hibrido + presencial).append("</span>\n");
        sb.append("          <span class=\"stat-label\">Presencial/Híbrido</span>\n");
        sb.append("        </div>\n");
        sb.append("      </div>\n");

        // Plataformas
        sb.append("      <div class=\"plataformas\">\n");
        sb.append("        <span class=\"plat-label\">Por plataforma:</span>\n");
        for (Map.Entry<String, Long> entry : porPlataforma.entrySet()) {
            sb.append("        <span class=\"plat-badge plat-").append(entry.getKey().toLowerCase()).append("\">");
            sb.append(entry.getKey()).append(" (").append(entry.getValue()).append(")</span>\n");
        }
        sb.append("      </div>\n");
        sb.append("    </section>\n");

        // Filtros
        sb.append("    <section class=\"filtros\">\n");
        sb.append("      <div class=\"filtros-row\">\n");
        sb.append("        <span class=\"filtro-label\">Filtrar:</span>\n");
        sb.append("        <button class=\"btn-filtro active\" onclick=\"filtrar('todos')\">Todos</button>\n");
        sb.append("        <button class=\"btn-filtro\" onclick=\"filtrar('remoto')\">Remoto</button>\n");
        sb.append("        <button class=\"btn-filtro\" onclick=\"filtrar('presencial')\">Presencial/Híbrido</button>\n");
        sb.append("        <button class=\"btn-filtro\" onclick=\"filtrar('senior')\">Sênior</button>\n");
        sb.append("        <button class=\"btn-filtro\" onclick=\"filtrar('pleno')\">Pleno</button>\n");
        for (String plataforma : porPlataforma.keySet()) {
            String slug = slugPlataforma(plataforma);
            sb.append("        <button class=\"btn-filtro\" onclick=\"filtrar('").append(slug).append("')\">");
            sb.append(escapeHtml(plataforma)).append("</button>\n");
        }
        sb.append("      </div>\n");
        sb.append("      <div class=\"busca-row\">\n");
        sb.append("        <input type=\"text\" id=\"busca\" placeholder=\"Buscar por título, empresa ou tecnologia...\" oninput=\"buscarVagas()\">\n");
        sb.append("        <span id=\"contador-vagas\" class=\"contador\">").append(total).append(" vagas exibidas</span>\n");
        sb.append("      </div>\n");
        sb.append("    </section>\n");

        return sb.toString();
    }

    private String gerarSecaoVagas(List<Vaga> vagas) {
        StringBuilder sb = new StringBuilder();
        sb.append("    <section class=\"vagas-grid\" id=\"vagas-grid\">\n");

        if (vagas.isEmpty()) {
            sb.append("      <div class=\"sem-vagas\">\n");
            sb.append("        <p>Nenhuma vaga encontrada com os critérios configurados.</p>\n");
            sb.append("        <p>Tente executar novamente mais tarde ou ajuste os parâmetros em <code>ConfiguracaoBusca.java</code>.</p>\n");
            sb.append("      </div>\n");
        } else {
            for (Vaga vaga : vagas) {
                sb.append(gerarCardVaga(vaga));
            }
        }

        sb.append("    </section>\n");
        return sb.toString();
    }

    private String gerarCardVaga(Vaga vaga) {
        String modalidadeClass = "";
        String modalidadeLabel = vaga.getModalidade() != null ? vaga.getModalidade() : "Não informado";
        if (modalidadeLabel.contains("Remoto")) modalidadeClass = "badge-remoto";
        else if (modalidadeLabel.contains("Híbrido")) modalidadeClass = "badge-hibrido";
        else modalidadeClass = "badge-presencial";

        String nivelClass = "Sênior".equals(vaga.getNivel()) ? "badge-senior" : "badge-pleno";
        String nivelLabel = vaga.getNivel() != null && !vaga.getNivel().isEmpty() ? vaga.getNivel() : "Não especificado";

        String plataformaClass = "plat-" + slugPlataforma(vaga.getPlataforma());

        // Atributos de dados para filtro
        String modalidadeData = modalidadeLabel.toLowerCase().contains("remoto") ? "remoto" :
                                modalidadeLabel.toLowerCase().contains("híbrido") ? "presencial" : "presencial";
        String nivelData = nivelLabel.toLowerCase().contains("sênior") ? "senior" : "pleno";
        String plataformaData = slugPlataforma(vaga.getPlataforma());

        String techs = vaga.getTecnologias() != null && !vaga.getTecnologias().isEmpty()
                ? vaga.getTecnologias() : "";

        String empresa = escapeHtml(vaga.getEmpresa() != null ? vaga.getEmpresa() : "Não informada");
        String titulo = escapeHtml(vaga.getTitulo() != null ? vaga.getTitulo() : "Sem título");
        String localizacao = escapeHtml(vaga.getLocalizacao() != null ? vaga.getLocalizacao() : "Não informada");
        String dataPublicacao = escapeHtml(vaga.getDataPublicacao() != null ? vaga.getDataPublicacao() : "Recente");
        String urlVaga = vaga.getUrlVaga() != null ? vaga.getUrlVaga() : "#";

        return "      <div class=\"vaga-card\" " +
               "data-modalidade=\"" + modalidadeData + "\" " +
               "data-nivel=\"" + nivelData + "\" " +
               "data-plataforma=\"" + plataformaData + "\" " +
               "data-texto=\"" + titulo.toLowerCase() + " " + empresa.toLowerCase() + " " + techs.toLowerCase() + "\">\n" +
               "        <div class=\"vaga-header\">\n" +
               "          <span class=\"plat-badge " + plataformaClass + "\">" + escapeHtml(vaga.getPlataforma()) + "</span>\n" +
               "          <span class=\"badge " + nivelClass + "\">" + nivelLabel + "</span>\n" +
               "          <span class=\"badge " + modalidadeClass + "\">" + modalidadeLabel + "</span>\n" +
               "        </div>\n" +
               "        <h3 class=\"vaga-titulo\">" + titulo + "</h3>\n" +
               "        <p class=\"vaga-empresa\">🏢 " + empresa + "</p>\n" +
               "        <p class=\"vaga-local\">📍 " + localizacao + "</p>\n" +
               (techs.isEmpty() ? "" :
               "        <div class=\"vaga-techs\">" + gerarTagsTecnologia(techs) + "</div>\n") +
               "        <div class=\"vaga-footer\">\n" +
               "          <span class=\"vaga-data\">📅 " + dataPublicacao + "</span>\n" +
               "          <a href=\"" + urlVaga + "\" target=\"_blank\" rel=\"noopener noreferrer\" class=\"btn-candidatar\">Ver Vaga →</a>\n" +
               "        </div>\n" +
               "      </div>\n";
    }

    private String gerarTagsTecnologia(String techs) {
        if (techs == null || techs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String tech : techs.split(",")) {
            String t = tech.trim();
            if (!t.isEmpty()) {
                sb.append("<span class=\"tech-tag\">").append(escapeHtml(t)).append("</span>");
            }
        }
        return sb.toString();
    }

    private String gerarRodapeHTML() {
        return "  </main>\n" +
               "  <footer class=\"footer\">\n" +
               "    <p>Robô de Vagas Java &nbsp;|&nbsp; Gerado em " +
               LocalDateTime.now().format(FORMATTER_EXIBICAO) +
               " &nbsp;|&nbsp; Vagas dos últimos 14 dias</p>\n" +
               "  </footer>\n" +
               "  <script>\n" +
               getJavaScript() +
               "  </script>\n" +
               "</body>\n" +
               "</html>\n";
    }

    private String getCSSEstilo() {
        return "    * { box-sizing: border-box; margin: 0; padding: 0; }\n" +
               "    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f0f2f5; color: #1a1a2e; }\n" +
               "    .header { background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%); color: white; padding: 24px 0; }\n" +
               "    .header-content { max-width: 1400px; margin: 0 auto; padding: 0 24px; display: flex; align-items: center; gap: 20px; }\n" +
               "    .header-icon { font-size: 48px; }\n" +
               "    .header h1 { font-size: 28px; font-weight: 700; margin-bottom: 4px; }\n" +
               "    .header-sub { font-size: 13px; opacity: 0.8; }\n" +
               "    .container { max-width: 1400px; margin: 0 auto; padding: 24px; }\n" +
               "    .status-fontes { background: white; border-radius: 12px; padding: 16px 20px; margin-bottom: 20px; }\n" +
               "    .tabela-status { width: 100%; border-collapse: collapse; font-size: 13px; }\n" +
               "    .tabela-status th, .tabela-status td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #eee; }\n" +
               "    .tabela-status th { color: #666; font-size: 12px; text-transform: uppercase; }\n" +
               "    .resumo { margin-bottom: 20px; }\n" +
               "    .stats-grid { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; }\n" +
               "    .stat-card { background: white; border-radius: 12px; padding: 16px 24px; text-align: center; min-width: 120px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); border-top: 4px solid #ccc; }\n" +
               "    .stat-total { border-top-color: #0f3460; }\n" +
               "    .stat-senior { border-top-color: #e94560; }\n" +
               "    .stat-pleno { border-top-color: #533483; }\n" +
               "    .stat-remoto { border-top-color: #0a9396; }\n" +
               "    .stat-hibrido { border-top-color: #ee9b00; }\n" +
               "    .stat-num { display: block; font-size: 32px; font-weight: 700; color: #0f3460; }\n" +
               "    .stat-label { font-size: 12px; color: #666; text-transform: uppercase; letter-spacing: 0.5px; }\n" +
               "    .plataformas { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }\n" +
               "    .plat-label { font-size: 13px; color: #666; font-weight: 600; }\n" +
               "    .filtros { background: white; border-radius: 12px; padding: 16px 20px; margin-bottom: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }\n" +
               "    .filtros-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }\n" +
               "    .filtro-label { font-size: 13px; color: #666; font-weight: 600; }\n" +
               "    .btn-filtro { padding: 6px 14px; border: 2px solid #e0e0e0; background: white; border-radius: 20px; cursor: pointer; font-size: 13px; transition: all 0.2s; }\n" +
               "    .btn-filtro:hover, .btn-filtro.active { background: #0f3460; color: white; border-color: #0f3460; }\n" +
               "    .busca-row { display: flex; align-items: center; gap: 12px; }\n" +
               "    .busca-row input { flex: 1; padding: 10px 16px; border: 2px solid #e0e0e0; border-radius: 8px; font-size: 14px; outline: none; transition: border-color 0.2s; }\n" +
               "    .busca-row input:focus { border-color: #0f3460; }\n" +
               "    .contador { font-size: 13px; color: #666; white-space: nowrap; }\n" +
               "    .vagas-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 16px; }\n" +
               "    .vaga-card { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); transition: transform 0.2s, box-shadow 0.2s; border-left: 4px solid #0f3460; }\n" +
               "    .vaga-card:hover { transform: translateY(-2px); box-shadow: 0 8px 24px rgba(0,0,0,0.12); }\n" +
               "    .vaga-header { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 12px; }\n" +
               "    .badge { padding: 3px 10px; border-radius: 12px; font-size: 11px; font-weight: 600; text-transform: uppercase; }\n" +
               "    .badge-senior { background: #fde8ec; color: #c0392b; }\n" +
               "    .badge-pleno { background: #ede7f6; color: #6a1b9a; }\n" +
               "    .badge-remoto { background: #e0f7fa; color: #00695c; }\n" +
               "    .badge-hibrido { background: #fff3e0; color: #e65100; }\n" +
               "    .badge-presencial { background: #e8f5e9; color: #2e7d32; }\n" +
               "    .plat-badge { padding: 3px 10px; border-radius: 12px; font-size: 11px; font-weight: 700; text-transform: uppercase; }\n" +
               "    .plat-linkedin { background: #0077b5; color: white; }\n" +
               "    .plat-indeed { background: #003a9b; color: white; }\n" +
               "    .plat-infojobs { background: #00a650; color: white; }\n" +
               "    .plat-catho { background: #e31837; color: white; }\n" +
               "    .plat-gupy { background: #1a73e8; color: white; }\n" +
               "    .plat-vagascom { background: #ff6b00; color: white; }\n" +
               "    .plat-programathor { background: #6c2bd9; color: white; }\n" +
               "    .plat-outro { background: #666; color: white; }\n" +
               "    .vaga-titulo { font-size: 16px; font-weight: 700; color: #1a1a2e; margin-bottom: 8px; line-height: 1.3; }\n" +
               "    .vaga-empresa { font-size: 14px; color: #444; margin-bottom: 4px; }\n" +
               "    .vaga-local { font-size: 13px; color: #666; margin-bottom: 10px; }\n" +
               "    .vaga-techs { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 12px; }\n" +
               "    .tech-tag { background: #f0f4ff; color: #0f3460; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }\n" +
               "    .vaga-footer { display: flex; justify-content: space-between; align-items: center; margin-top: 12px; padding-top: 12px; border-top: 1px solid #f0f0f0; }\n" +
               "    .vaga-data { font-size: 12px; color: #999; }\n" +
               "    .btn-candidatar { background: #0f3460; color: white; padding: 8px 16px; border-radius: 8px; text-decoration: none; font-size: 13px; font-weight: 600; transition: background 0.2s; }\n" +
               "    .btn-candidatar:hover { background: #e94560; }\n" +
               "    .sem-vagas { grid-column: 1/-1; text-align: center; padding: 60px 20px; color: #666; }\n" +
               "    .footer { text-align: center; padding: 24px; color: #999; font-size: 13px; margin-top: 40px; }\n" +
               "    @media (max-width: 600px) { .vagas-grid { grid-template-columns: 1fr; } .stats-grid { justify-content: center; } }\n";
    }

    private String getJavaScript() {
        return "    let filtroAtual = 'todos';\n" +
               "    function filtrar(tipo) {\n" +
               "      filtroAtual = tipo;\n" +
               "      document.querySelectorAll('.btn-filtro').forEach(b => b.classList.remove('active'));\n" +
               "      event.target.classList.add('active');\n" +
               "      aplicarFiltros();\n" +
               "    }\n" +
               "    function buscarVagas() { aplicarFiltros(); }\n" +
               "    function aplicarFiltros() {\n" +
               "      const busca = document.getElementById('busca').value.toLowerCase();\n" +
               "      const cards = document.querySelectorAll('.vaga-card');\n" +
               "      let visiveis = 0;\n" +
               "      cards.forEach(card => {\n" +
               "        const modalidade = card.dataset.modalidade || '';\n" +
               "        const nivel = card.dataset.nivel || '';\n" +
               "        const plataforma = card.dataset.plataforma || '';\n" +
               "        const texto = card.dataset.texto || '';\n" +
               "        let mostrar = true;\n" +
               "        if (filtroAtual === 'remoto') mostrar = modalidade === 'remoto';\n" +
               "        else if (filtroAtual === 'presencial') mostrar = modalidade === 'presencial';\n" +
               "        else if (filtroAtual === 'senior') mostrar = nivel === 'senior';\n" +
               "        else if (filtroAtual === 'pleno') mostrar = nivel === 'pleno';\n" +
               "        else if (filtroAtual !== 'todos') mostrar = plataforma === filtroAtual;\n" +
               "        if (mostrar && busca) mostrar = texto.includes(busca);\n" +
               "        card.style.display = mostrar ? '' : 'none';\n" +
               "        if (mostrar) visiveis++;\n" +
               "      });\n" +
               "      document.getElementById('contador-vagas').textContent = visiveis + ' vagas exibidas';\n" +
               "    }\n";
    }

    private String slugPlataforma(String plataforma) {
        if (plataforma == null || plataforma.isBlank()) {
            return "outro";
        }
        return plataforma.toLowerCase().replaceAll("[^a-z0-9]+", "");
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
