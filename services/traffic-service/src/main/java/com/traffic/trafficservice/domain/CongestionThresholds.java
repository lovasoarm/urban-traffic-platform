package com.traffic.trafficservice.domain;

public record CongestionThresholds(double freeRatio, double denseRatio, double congestedRatio) {
    public CongestionThresholds {
        if (!(freeRatio > denseRatio && denseRatio > congestedRatio && congestedRatio >= 0.0)) {
            throw new IllegalArgumentException("Congestion thresholds must be descending and non-negative");
        }
    }
}
