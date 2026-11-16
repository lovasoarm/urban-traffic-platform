package com.traffic.historyservice.exception;

import org.springframework.http.HttpStatus;

public class HistoryException extends RuntimeException {
    private final HttpStatus status;
    public HistoryException(String message, HttpStatus status) { super(message); this.status = status; }
    public HttpStatus status() { return status; }
}
