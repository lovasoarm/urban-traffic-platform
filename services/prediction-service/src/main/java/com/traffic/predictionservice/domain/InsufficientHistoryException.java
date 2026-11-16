package com.traffic.predictionservice.domain;

public class InsufficientHistoryException extends RuntimeException {
    public InsufficientHistoryException(String message) { super(message); }
}
