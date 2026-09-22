package br.com.vagas.model;

import java.time.LocalDate;

/**
 * Representa uma vaga de emprego encontrada nas plataformas.
 */
public class Vaga {

    private String titulo;
    private String empresa;
    private String localizacao;
    private String modalidade;   // Remoto, Presencial, Híbrido
    private String dataPublicacao;
    private LocalDate dataPublicacaoDate;
    private String descricao;
    private String urlVaga;
    private String plataforma;
    private String nivel;        // Sênior, Pleno
    private String tecnologias;

    public Vaga() {}

    public Vaga(String titulo, String empresa, String localizacao, String modalidade,
                String dataPublicacao, String descricao, String urlVaga, String plataforma) {
        this.titulo = titulo;
        this.empresa = empresa;
        this.localizacao = localizacao;
        this.modalidade = modalidade;
        this.dataPublicacao = dataPublicacao;
        this.descricao = descricao;
        this.urlVaga = urlVaga;
        this.plataforma = plataforma;
    }

    // Getters e Setters
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getEmpresa() { return empresa; }
    public void setEmpresa(String empresa) { this.empresa = empresa; }

    public String getLocalizacao() { return localizacao; }
    public void setLocalizacao(String localizacao) { this.localizacao = localizacao; }

    public String getModalidade() { return modalidade; }
    public void setModalidade(String modalidade) { this.modalidade = modalidade; }

    public String getDataPublicacao() { return dataPublicacao; }
    public void setDataPublicacao(String dataPublicacao) { this.dataPublicacao = dataPublicacao; }

    public LocalDate getDataPublicacaoDate() { return dataPublicacaoDate; }
    public void setDataPublicacaoDate(LocalDate dataPublicacaoDate) { this.dataPublicacaoDate = dataPublicacaoDate; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getUrlVaga() { return urlVaga; }
    public void setUrlVaga(String urlVaga) { this.urlVaga = urlVaga; }

    public String getPlataforma() { return plataforma; }
    public void setPlataforma(String plataforma) { this.plataforma = plataforma; }

    public String getNivel() { return nivel; }
    public void setNivel(String nivel) { this.nivel = nivel; }

    public String getTecnologias() { return tecnologias; }
    public void setTecnologias(String tecnologias) { this.tecnologias = tecnologias; }

    @Override
    public String toString() {
        return String.format("[%s] %s - %s | %s | %s | %s",
                plataforma, titulo, empresa, localizacao, modalidade, dataPublicacao);
    }
}
