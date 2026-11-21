package com.fithub.identity.interfaces.dto;

/**
 * DTO para resposta de erro padronizada.
 */
public class ErrorResponse {
    private String message;
    private String code;
    private long timestamp;

    public ErrorResponse(String message) {
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    public ErrorResponse(String message, String code) {
        this.message = message;
        this.code = code;
        this.timestamp = System.currentTimeMillis();
    }

    public String getMessage() { return message; }
    public String getCode() { return code; }
    public long getTimestamp() { return timestamp; }
}
