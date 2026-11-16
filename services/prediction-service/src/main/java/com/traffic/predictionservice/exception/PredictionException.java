package com.traffic.predictionservice.exception;

import org.springframework.http.HttpStatus;

public class PredictionException extends RuntimeException {
    private final HttpStatus status;
    public PredictionException(String message, HttpStatus status) { super(message); this.status = status; }
    public HttpStatus status() { return status; }
}
