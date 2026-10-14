package br.com.vagas.scraper;

import br.com.vagas.config.ConfiguracaoBusca;
import br.com.vagas.model.ResultadoBusca;
import br.com.vagas.model.Vaga;
import br.com.vagas.util.DataUtil;
import br.com.vagas.util.FalhaHttpException;
import br.com.vagas.util.HttpUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Scraper do Sólides Vagas ({@code https://vagas.solides.com.br/vagas/{termo}/remoto}).
 * A página é Next.js: as vagas vêm no payload {@code self.__next_f.push} dentro de
 * {@code "initialData":{"totalPages":..,"data":[...]}}.
 */
public class SolidesScraper extends ScraperBase {

    private static final Logger logger = LoggerFactory.getLogger(SolidesScraper.class);
    private static final String BASE_URL = "https://vagas.solides.com.br";
    private static final String REFERER = BASE_URL + "/";
    private static final String PREFIXO_RSC = "self.__next_f.push([1,";

    @Override
    public String getNomePlataforma() {
        return "Sólides";
    }

    @Override
    public ResultadoBusca buscarVagas() {
        List<Vaga> todasVagas = new ArrayList<>();
        Set<String> vistas = new LinkedHashSet<>();
        falhaHttp = null;
        logger.info("Iniciando busca no Sólides Vagas...");

        List<String> termos = Arrays.asList("java", "spring-boot");
        for (String termo : termos) {
            logger.info("Sólides - Buscando remotas: '{}'", termo);
            List<Vaga> vagas = buscarTermo(termo, vistas);
            todasVagas.addAll(vagas);
            logger.info("Sólides - Encontradas {} vagas remotas para '{}'", vagas.size(), termo);

            if (vagas.isEmpty() && falhaHttp != null) {
                break;
            }
        }

        logger.info("Sólides - Total de vagas encontradas: {}", todasVagas.size());
        return fecharResultado(todasVagas);
    }

    private List<Vaga> buscarTermo(String termo, Set<String> vistas) {
        List<Vaga> vagas = new ArrayList<>();
        int maxPaginas = 5;

        for (int pagina = 1; pagina <= maxPaginas; pagina++) {
            try {
                String url = BASE_URL + "/vagas/" + termo + "/remoto" + (pagina > 1 ? "?page=" + pagina : "");
                logger.debug("Sólides URL: {}", url);

                Document doc = HttpUtil.get(url, REFERER);
                if (documentoBloqueado(doc)) {
                    registrarFalha(new FalhaHttpException(FalhaHttpException.Tipo.BLOQUEIO,
                            "BLOQUEIO ANTI-BOT no Sólides"), "Sólides");
                    break;
                }

                JsonObject initialData = extrairInitialData(doc);
                if (initialData == null || !initialData.has("data") || !initialData.get("data").isJsonArray()) {
                    logger.debug("Sólides - initialData não encontrado na página {}", pagina);
                    break;
                }

                JsonArray data = initialData.getAsJsonArray("data");
                if (data.size() == 0) {
                    break;
                }
                int extraidas = 0;
                boolean algumaNoPrazo = false;
                for (JsonElement el : data) {
                    if (!el.isJsonObject()) {
                        continue;
                    }
                    LocalDate criada = DataUtil.parsearData(texto(el.getAsJsonObject(), "createdAt"));
                    if (DataUtil.isDentroDoLimite(criada, ConfiguracaoBusca.MAX_DIAS_PUBLICACAO)) {
                        algumaNoPrazo = true;
                    }
                    Vaga vaga = extrairVaga(el.getAsJsonObject());
                    if (vaga != null && vistas.add((vaga.getTitulo() + "|" + vaga.getEmpresa()).toLowerCase())) {
                        vagas.add(vaga);
                        extraidas++;
                    }
                }
                logger.debug("Sólides - Página {}: {} vagas extraídas de {}", pagina, extraidas, data.size());

                int totalPaginas = inteiro(initialData, "totalPages");
                if (totalPaginas > 0 && pagina >= totalPaginas) {
                    break;
                }
                // a listagem vem da mais recente para a mais antiga
                if (!algumaNoPrazo) {
                    break;
                }
            } catch (IOException e) {
                registrarFalha(e, "Sólides - página " + pagina);
                break;
            } catch (Exception e) {
                logger.error("Sólides - ERRO DE PARSING: {}", e.getMessage());
                if (falhaHttp == null) {
                    falhaHttp = new IOException("ERRO DE PARSING: " + e.getMessage(), e);
                }
                break;
            }
        }
        return vagas;
    }

    /**
     * Junta os trechos do payload RSC (cada push é uma string JSON) e extrai {@code initialData}.
     */
    private JsonObject extrairInitialData(Document doc) {
        StringBuilder payload = new StringBuilder();
        for (Element script : doc.select("script")) {
            String corpo = script.data().trim();
            if (!corpo.startsWith(PREFIXO_RSC) || !corpo.endsWith("])")) {
                continue;
            }
            String literal = corpo.substring(PREFIXO_RSC.length(), corpo.length() - 2);
            try {
                payload.append(JsonParser.parseString(literal).getAsString());
            } catch (Exception ignored) {
                // push que não é string (ex.: marcadores numéricos)
            }
        }
        String json = extrairObjetoJsonApos(payload.toString(), "\"initialData\":");
        if (json == null) {
            return null;
        }
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private Vaga extrairVaga(JsonObject job) {
        String titulo = texto(job, "title");
        if (titulo.isEmpty()) {
            return null;
        }
        String tipo = texto(job, "jobType");
        if (!tipo.isEmpty() && !"remoto".equalsIgnoreCase(tipo)) {
            return null;
        }

        String empresa = texto(job, "companyName");
        String localizacao = montarLocal(job);
        String dataTexto = texto(job, "createdAt");
        String urlVaga = montarUrl(job);

        Vaga vaga = montarVaga(titulo, empresa, localizacao, "Remoto", dataTexto, urlVaga, getNomePlataforma(), "");
        if (vaga == null) {
            return null;
        }

        String descricao = texto(job, "description");
        if (!descricao.isEmpty() && !descricao.startsWith("$")) {
            String techs = extrairTecnologias(titulo + " " + descricao);
            if (!techs.isEmpty()) {
                vaga.setTecnologias(techs);
            }
            String resumo = descricao.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
            if (resumo.length() > 280) {
                resumo = resumo.substring(0, 280) + "...";
            }
            vaga.setDescricao(resumo);
        }
        return vaga;
    }

    private String montarLocal(JsonObject job) {
        List<String> partes = new ArrayList<>();
        String cidade = nome(job, "city");
        String estado = job.has("state") && job.get("state").isJsonObject()
                ? texto(job.getAsJsonObject("state"), "code") : "";
        String pais = nome(job, "country");
        if (!cidade.isEmpty()) partes.add(cidade);
        if (!estado.isEmpty()) partes.add(estado);
        partes.add(pais.isEmpty() ? "Brasil" : pais);
        return String.join(", ", partes);
    }

    private String montarUrl(JsonObject job) {
        if (job.has("publishers") && job.get("publishers").isJsonObject()) {
            JsonObject publishers = job.getAsJsonObject("publishers");
            if (publishers.has("rhgestor") && publishers.get("rhgestor").isJsonObject()) {
                String url = texto(publishers.getAsJsonObject("rhgestor"), "redirectUrl");
                if (!url.isEmpty()) {
                    return url;
                }
            }
        }
        String slug = texto(job, "slug");
        String id = texto(job, "id");
        if (!slug.isEmpty() && !id.isEmpty()) {
            return "https://" + slug + ".vagas.solides.com.br/vaga/" + id;
        }
        return BASE_URL + "/vagas";
    }

    private String nome(JsonObject job, String chave) {
        if (job.has(chave) && job.get(chave).isJsonObject()) {
            return texto(job.getAsJsonObject(chave), "name");
        }
        return "";
    }

    private int inteiro(JsonObject obj, String chave) {
        try {
            return obj.has(chave) && !obj.get(chave).isJsonNull() ? obj.get(chave).getAsInt() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String texto(JsonObject obj, String chave) {
        if (obj == null || !obj.has(chave) || obj.get(chave).isJsonNull() || !obj.get(chave).isJsonPrimitive()) {
            return "";
        }
        return obj.get(chave).getAsString().trim();
    }
}
