package br.com.vagas.diagnostico;

import br.com.vagas.model.ResultadoBusca;
import br.com.vagas.scraper.CathoScraper;
import br.com.vagas.scraper.GupyScraper;
import br.com.vagas.scraper.IndeedScraper;
import br.com.vagas.scraper.InfoJobsScraper;
import br.com.vagas.scraper.LinkedInScraper;
import br.com.vagas.scraper.ProgramathorScraper;
import br.com.vagas.scraper.ScraperVagas;
import br.com.vagas.scraper.VagasComScraper;
import br.com.vagas.util.SslAmbiente;

import java.util.Arrays;
import java.util.List;

/**
 * Executa cada scraper uma vez e imprime o status classificado.
 */
public class DiagnosticoScrapers {

    public static void main(String[] args) {
        SslAmbiente.garantirTrustStorePadrao();
        List<ScraperVagas> scrapers = Arrays.asList(
                new LinkedInScraper(),
                new GupyScraper(),
                new VagasComScraper(),
                new ProgramathorScraper(),
                new CathoScraper(),
                new InfoJobsScraper(),
                new IndeedScraper()
        );
        for (ScraperVagas scraper : scrapers) {
            System.out.println("========== " + scraper.getNomePlataforma() + " ==========");
            try {
                ResultadoBusca r = scraper.buscarVagas();
                System.out.println("PLATAFORMA: " + r.getPlataforma());
                System.out.println("STATUS: " + r.getStatus());
                System.out.println("VAGAS ENCONTRADAS: " + r.getVagas().size());
                System.out.println("DETALHE: " + r.getDetalhe());
            } catch (Exception e) {
                System.out.println("PLATAFORMA: " + scraper.getNomePlataforma());
                System.out.println("STATUS: ERRO_CONEXAO");
                System.out.println("DETALHE: " + e.getMessage());
            }
            System.out.println();
        }
    }
}
