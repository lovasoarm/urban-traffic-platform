package com.traffic.trafficservice.domain;

public final class CongestionClassifier {
    private CongestionClassifier() { }

    public static CongestionLevel classify(double speedKmh, double speedLimitKmh, CongestionThresholds thresholds) {
        if (speedKmh < 0.0 || speedLimitKmh <= 0.0) {
            throw new IllegalArgumentException("Speeds must be non-negative and the speed limit positive");
        }
        double ratio = speedKmh / speedLimitKmh;
        if (ratio >= thresholds.freeRatio()) return CongestionLevel.FREE;
        if (ratio >= thresholds.denseRatio()) return CongestionLevel.DENSE;
        if (ratio >= thresholds.congestedRatio()) return CongestionLevel.CONGESTED;
        return CongestionLevel.BLOCKED;
    }
}
