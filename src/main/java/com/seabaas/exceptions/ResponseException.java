package com.seabaas.exceptions;

public class ResponseException extends RuntimeException {
    private final int status;

    public ResponseException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
