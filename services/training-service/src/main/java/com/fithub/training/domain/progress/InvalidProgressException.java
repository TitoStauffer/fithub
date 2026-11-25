package com.fithub.training.domain.progress;

/** Parâmetro da consulta de evolução fora das regras; o campo vai na resposta. */
public final class InvalidProgressException extends RuntimeException {

    private final String field;

    public InvalidProgressException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
