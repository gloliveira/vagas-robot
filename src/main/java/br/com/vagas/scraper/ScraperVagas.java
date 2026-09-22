package br.com.vagas.scraper;

import br.com.vagas.model.ResultadoBusca;

/**
 * Interface base para todos os scrapers de vagas.
 */
public interface ScraperVagas {

    String getNomePlataforma();

    /**
     * Busca vagas na plataforma e devolve o resultado classificado
     * (sucesso, sem vagas, bloqueio ou erro de conexão).
     */
    ResultadoBusca buscarVagas();
}
