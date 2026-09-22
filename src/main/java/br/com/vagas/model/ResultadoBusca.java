package br.com.vagas.model;

import br.com.vagas.util.FalhaHttpException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resultado de um scraper, distinguindo sucesso, vazio, bloqueio e erro de conexão.
 */
public class ResultadoBusca {

    public enum Status {
        SUCESSO,
        SEM_VAGAS,
        ERRO_CONEXAO,
        BLOQUEIO,
        HTTP_INESPERADO,
        ERRO_PARSING
    }

    private final String plataforma;
    private final Status status;
    private final List<Vaga> vagas;
    private final String detalhe;

    public ResultadoBusca(String plataforma, Status status, List<Vaga> vagas, String detalhe) {
        this.plataforma = plataforma;
        this.status = status;
        this.vagas = vagas == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(vagas));
        this.detalhe = detalhe == null ? "" : detalhe;
    }

    public static ResultadoBusca de(String plataforma, List<Vaga> vagas, IOException falha) {
        List<Vaga> lista = vagas == null ? Collections.emptyList() : vagas;
        if (!lista.isEmpty()) {
            String extra = falha != null ? " (busca parcial: " + falha.getMessage() + ")" : "";
            return new ResultadoBusca(plataforma, Status.SUCESSO, lista, lista.size() + " vagas" + extra);
        }
        if (falha instanceof FalhaHttpException) {
            FalhaHttpException fe = (FalhaHttpException) falha;
            Status st;
            switch (fe.getTipo()) {
                case BLOQUEIO:
                    st = Status.BLOQUEIO;
                    break;
                case HTTP_INESPERADO:
                    st = Status.HTTP_INESPERADO;
                    break;
                default:
                    st = Status.ERRO_CONEXAO;
            }
            return new ResultadoBusca(plataforma, st, lista, fe.getMessage());
        }
        if (falha != null) {
            return new ResultadoBusca(plataforma, Status.ERRO_CONEXAO, lista, falha.getMessage());
        }
        return new ResultadoBusca(plataforma, Status.SEM_VAGAS, lista, "HTTP OK, página processada, 0 vagas encontradas");
    }

    public String getPlataforma() { return plataforma; }
    public Status getStatus() { return status; }
    public List<Vaga> getVagas() { return vagas; }
    public String getDetalhe() { return detalhe; }

    public boolean coletouVagas() {
        return !vagas.isEmpty();
    }
}
