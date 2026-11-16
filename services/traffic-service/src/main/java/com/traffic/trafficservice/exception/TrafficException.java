package com.traffic.trafficservice.exception;

import org.springframework.http.HttpStatus;

public class TrafficException extends RuntimeException {
    private final HttpStatus status;

    public TrafficException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() { return status; }
}
