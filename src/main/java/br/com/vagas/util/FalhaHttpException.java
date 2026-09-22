package br.com.vagas.util;

import java.io.IOException;

/**
 * Falha HTTP classificada, para não mascarar infraestrutura como "0 vagas".
 */
public class FalhaHttpException extends IOException {

    public enum Tipo {
        ERRO_CONEXAO,
        BLOQUEIO,
        HTTP_INESPERADO
    }

    private final Tipo tipo;
    private final int httpStatus;

    public FalhaHttpException(Tipo tipo, String mensagem) {
        this(tipo, mensagem, -1, null);
    }

    public FalhaHttpException(Tipo tipo, String mensagem, int httpStatus) {
        this(tipo, mensagem, httpStatus, null);
    }

    public FalhaHttpException(Tipo tipo, String mensagem, int httpStatus, Throwable causa) {
        super(mensagem, causa);
        this.tipo = tipo;
        this.httpStatus = httpStatus;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
