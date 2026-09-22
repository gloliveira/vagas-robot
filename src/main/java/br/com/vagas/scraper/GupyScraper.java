package br.com.vagas.scraper;

import br.com.vagas.model.ResultadoBusca;
import br.com.vagas.model.Vaga;
import br.com.vagas.util.FiltroBrasil;
import br.com.vagas.util.HttpUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Scraper da Gupy via API pública do portal de vagas.
 * GET https://employability-portal.gupy.io/api/v1/jobs?jobName=...
 */
public class GupyScraper extends ScraperBase {

    private static final Logger logger = LoggerFactory.getLogger(GupyScraper.class);
    private static final String API_URL = "https://employability-portal.gupy.io/api/v1/jobs";
    private static final String REFERER = "https://portal.gupy.io/";

    @Override
    public String getNomePlataforma() {
        return "Gupy";
    }

    @Override
    public ResultadoBusca buscarVagas() {
        List<Vaga> todasVagas = new ArrayList<>();
        falhaHttp = null;
        logger.info("Iniciando busca na Gupy...");

        List<String> termos = Arrays.asList("java", "spring boot", "jsf");
        for (String termo : termos) {
            logger.info("Gupy - Buscando: '{}'", termo);
            List<Vaga> vagas = buscarTermo(termo);
            todasVagas.addAll(vagas);
            logger.info("Gupy - Encontradas {} vagas para '{}'", vagas.size(), termo);
        }

        logger.info("Gupy - Total de vagas encontradas: {}", todasVagas.size());
        return fecharResultado(todasVagas);
    }

    private List<Vaga> buscarTermo(String termo) {
        List<Vaga> vagas = new ArrayList<>();
        int limit = 10;
        int maxPaginas = 3;

        for (int pagina = 0; pagina < maxPaginas; pagina++) {
            int offset = pagina * limit;
            try {
                String url = API_URL
                        + "?jobName=" + URLEncoder.encode(termo, StandardCharsets.UTF_8)
                        + "&limit=" + limit
                        + "&offset=" + offset;
                logger.debug("Gupy URL: {}", url);

                String json = HttpUtil.getJson(url, REFERER);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                if (!root.has("data") || !root.get("data").isJsonArray()) {
                    logger.debug("Gupy - Resposta sem array data na página {}", pagina + 1);
                    break;
                }

                JsonArray data = root.getAsJsonArray("data");
                if (data.size() == 0) {
                    break;
                }

                int extraidas = 0;
                for (JsonElement el : data) {
                    Vaga vaga = extrairVaga(el.getAsJsonObject());
                    if (vaga != null) {
                        vagas.add(vaga);
                        extraidas++;
                    }
                }
                logger.debug("Gupy - Página {}: {} vagas extraídas de {}", pagina + 1, extraidas, data.size());

                if (root.has("pagination")) {
                    JsonObject pag = root.getAsJsonObject("pagination");
                    int total = pag.has("total") ? pag.get("total").getAsInt() : 0;
                    if (offset + limit >= total) {
                        break;
                    }
                }
            } catch (IOException e) {
                registrarFalha(e, "Gupy - página " + (pagina + 1));
                break;
            } catch (Exception e) {
                logger.error("Gupy - ERRO DE PARSING JSON: {}", e.getMessage());
                if (falhaHttp == null) {
                    falhaHttp = new IOException("ERRO DE PARSING: " + e.getMessage(), e);
                }
                break;
            }
        }
        return vagas;
    }

    private Vaga extrairVaga(JsonObject job) {
        String titulo = texto(job, "name");
        if (titulo.isEmpty()) {
            return null;
        }

        String empresa = texto(job, "careerPageName");
        String cidade = texto(job, "city");
        String estado = texto(job, "state");
        String pais = texto(job, "country");
        String workplace = texto(job, "workplaceType");
        boolean remoto = job.has("isRemoteWork") && !job.get("isRemoteWork").isJsonNull()
                && job.get("isRemoteWork").getAsBoolean();
        if (!remoto && !"remote".equalsIgnoreCase(workplace)) {
            return null;
        }
        if ("hybrid".equalsIgnoreCase(workplace)) {
            return null;
        }

        String localizacao = juntarLocal(cidade, estado, pais, remoto, workplace);
        if (!localizacaoCompativel(localizacao, pais, cidade, estado)) {
            return null;
        }

        String modalidade = "Remoto";

        String dataTexto = texto(job, "publishedDate");
        String urlVaga = texto(job, "jobUrl");
        String descricao = texto(job, "description");

        Vaga vaga = montarVaga(titulo, empresa, localizacao, modalidade, dataTexto, urlVaga, "Gupy", descricao);
        if (vaga != null && descricao != null && !descricao.isBlank()) {
            String resumo = descricao.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
            if (resumo.length() > 280) {
                resumo = resumo.substring(0, 280) + "...";
            }
            vaga.setDescricao(resumo);
        }
        return vaga;
    }

    private boolean localizacaoCompativel(String localizacao, String pais, String cidade, String estado) {
        if (FiltroBrasil.isPaisBrasil(pais)) {
            return FiltroBrasil.isVagaBrasileira(localizacao, pais, cidade, estado);
        }
        return FiltroBrasil.temSinalBrasil(localizacao, pais, cidade, estado)
                && FiltroBrasil.isVagaBrasileira(localizacao, pais, cidade, estado);
    }

    private String juntarLocal(String cidade, String estado, String pais, boolean remoto, String workplace) {
        if (remoto || "remote".equalsIgnoreCase(workplace)) {
            return "Remoto" + (pais.isEmpty() ? "" : " - " + pais);
        }
        List<String> partes = new ArrayList<>();
        if (!cidade.isEmpty()) partes.add(cidade);
        if (!estado.isEmpty()) partes.add(estado);
        if (!pais.isEmpty()) partes.add(pais);
        if ("hybrid".equalsIgnoreCase(workplace)) {
            partes.add("Híbrido");
        }
        return partes.isEmpty() ? "Não informada" : String.join(", ", partes);
    }

    private String texto(JsonObject obj, String chave) {
        if (obj == null || !obj.has(chave) || obj.get(chave).isJsonNull()) {
            return "";
        }
        try {
            return obj.get(chave).getAsString().trim();
        } catch (Exception e) {
            return "";
        }
    }
}
