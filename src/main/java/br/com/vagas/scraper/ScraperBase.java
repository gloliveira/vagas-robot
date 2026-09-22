package br.com.vagas.scraper;

import br.com.vagas.config.ConfiguracaoBusca;
import br.com.vagas.model.ResultadoBusca;
import br.com.vagas.model.Vaga;
import br.com.vagas.util.DataUtil;
import br.com.vagas.util.FiltroBrasil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Base comum dos scrapers: relevância, modalidade, nível e montagem da vaga.
 */
public abstract class ScraperBase implements ScraperVagas {

    private static final Logger loggerBase = LoggerFactory.getLogger(ScraperBase.class);
    protected IOException falhaHttp;

    protected ResultadoBusca fecharResultado(List<Vaga> vagas) {
        return ResultadoBusca.de(getNomePlataforma(), vagas, falhaHttp);
    }

    protected void registrarFalha(IOException e, String contexto) {
        if (this.falhaHttp == null) {
            this.falhaHttp = e;
        }
        loggerBase.error("{}: {}", contexto, e.getMessage());
    }

    protected boolean isVagaRelevante(String titulo) {
        if (titulo == null || titulo.isBlank()) {
            return false;
        }
        String t = titulo.toLowerCase();
        return t.contains("java")
                || t.contains("spring")
                || t.contains("jsf")
                || t.contains("primefaces")
                || t.contains("backend")
                || t.contains("back-end")
                || t.contains("back end");
    }

    protected String detectarModalidade(String localizacao) {
        return detectarModalidade(localizacao, "Não informado");
    }

    protected String detectarModalidade(String localizacao, String fallback) {
        if (localizacao == null || localizacao.isBlank()) {
            return fallback != null ? fallback : "Não informado";
        }
        String loc = localizacao.toLowerCase();
        if (loc.contains("remoto") || loc.contains("remote") || loc.contains("home office")
                || loc.contains("trabalhe de casa")) {
            return "Remoto";
        }
        if (loc.contains("híbrido") || loc.contains("hibrido") || loc.contains("hybrid")) {
            return "Híbrido";
        }
        if (loc.contains("presencial") || loc.contains("on-site") || loc.contains("onsite")) {
            return "Presencial";
        }
        return fallback != null ? fallback : "Presencial";
    }

    protected boolean isVagaRemota(String modalidade, String... textos) {
        StringBuilder sb = new StringBuilder(modalidade == null ? "" : modalidade);
        if (textos != null) {
            for (String t : textos) {
                if (t != null && !t.isBlank()) {
                    sb.append(' ').append(t);
                }
            }
        }
        String blob = sb.toString().toLowerCase();
        if (blob.contains("híbrido") || blob.contains("hibrido") || blob.contains("hybrid")) {
            return false;
        }
        return "Remoto".equalsIgnoreCase(modalidade)
                || blob.contains("remoto")
                || blob.contains("remote")
                || blob.contains("home office")
                || blob.contains("trabalhe de casa")
                || blob.contains("work from home");
    }

    protected String detectarNivel(String titulo) {
        if (titulo == null) {
            return "Não especificado";
        }
        String t = titulo.toLowerCase();
        if (t.contains("sênior") || t.contains("senior") || t.contains("sr.") || t.contains(" sr")
                || t.contains("sr ") || t.endsWith(" sr") || t.contains("especialista")) {
            return "Sênior";
        }
        if (t.contains("pleno") || t.contains("mid-level") || t.contains("mid level")
                || t.contains("pl.") || t.contains(" pl") || t.endsWith(" pl")) {
            return "Pleno";
        }
        if (t.contains("júnior") || t.contains("junior") || t.contains("jr.") || t.contains("estagi")) {
            return "Júnior";
        }
        return "Não especificado";
    }

    protected String extrairTecnologias(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        List<String> encontradas = new ArrayList<>();
        String lower = texto.toLowerCase();
        for (String tech : ConfiguracaoBusca.TECNOLOGIAS_RELEVANTES) {
            if (lower.contains(tech.toLowerCase()) && !encontradas.contains(tech)) {
                encontradas.add(tech);
            }
        }
        return String.join(", ", encontradas);
    }

    protected String absolutizarUrl(String url, String base) {
        if (url == null || url.isBlank() || url.equals("#")) {
            return "#";
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        if (!url.startsWith("/")) {
            url = "/" + url;
        }
        return base + url;
    }

    protected String texto(Element el) {
        return el != null ? el.text().trim() : "";
    }

    protected String primeiroTexto(Element raiz, String... seletores) {
        for (String seletor : seletores) {
            Element el = raiz.selectFirst(seletor);
            if (el != null) {
                String t = el.text().trim();
                if (!t.isEmpty()) {
                    return t;
                }
            }
        }
        return "";
    }

    protected String primeiroHref(Element raiz, String... seletores) {
        for (String seletor : seletores) {
            Element el = raiz.selectFirst(seletor);
            if (el != null) {
                String href = el.absUrl("href");
                if (href == null || href.isBlank()) {
                    href = el.attr("href");
                }
                if (href != null && !href.isBlank()) {
                    return href;
                }
            }
        }
        return "";
    }

    protected Vaga montarVaga(String titulo, String empresa, String localizacao, String modalidade,
                              String dataTexto, String url, String plataforma, String tecnologiasExtra) {
        String blobRelevancia = titulo + " " + (tecnologiasExtra != null ? tecnologiasExtra : "");
        if (!isVagaRelevante(blobRelevancia)) {
            return null;
        }
        if (!FiltroBrasil.isVagaBrasileira(localizacao)) {
            return null;
        }
        String modalidadeFinal = detectarModalidade(localizacao, modalidade);
        if (ConfiguracaoBusca.SOMENTE_REMOTAS
                && !isVagaRemota(modalidadeFinal, titulo, localizacao)) {
            return null;
        }
        LocalDate dataPublicacao = DataUtil.parsearData(dataTexto);
        if (!DataUtil.isDentroDoLimite(dataPublicacao, ConfiguracaoBusca.MAX_DIAS_PUBLICACAO)) {
            return null;
        }
        Vaga vaga = new Vaga();
        vaga.setTitulo(titulo.trim());
        vaga.setEmpresa(empresa == null || empresa.isBlank() ? "Não informada" : empresa.trim());
        vaga.setLocalizacao(localizacao == null || localizacao.isBlank() ? "Não informada" : localizacao.trim());
        vaga.setModalidade(modalidadeFinal);
        vaga.setDataPublicacao(dataTexto == null || dataTexto.isBlank() ? "Recente" : dataTexto.trim());
        vaga.setDataPublicacaoDate(dataPublicacao);
        vaga.setUrlVaga(url == null || url.isBlank() ? "#" : url);
        vaga.setPlataforma(plataforma);
        vaga.setNivel(detectarNivel(titulo));
        String techs = extrairTecnologias(titulo + " " + (tecnologiasExtra != null ? tecnologiasExtra : ""));
        vaga.setTecnologias(techs);
        return vaga;
    }

    protected boolean documentoBloqueado(Document doc) {
        if (doc == null) {
            return true;
        }
        String titulo = doc.title() != null ? doc.title().toLowerCase() : "";
        return titulo.contains("security check")
                || titulo.contains("just a moment")
                || titulo.contains("attention required")
                || titulo.contains("access denied");
    }
}
