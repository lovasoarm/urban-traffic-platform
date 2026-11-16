package com.traffic.trafficservice.domain;

import java.util.List;

public final class TrafficAggregation {
    private TrafficAggregation() { }

    public static SpeedStatistics summarizeSpeeds(List<Double> speeds) {
        if (speeds == null || speeds.isEmpty()) throw new IllegalArgumentException("At least one speed is required");
        double average = speeds.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double minimum = speeds.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
        double maximum = speeds.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        return new SpeedStatistics(average, minimum, maximum, speeds.size());
    }

    public record SpeedStatistics(double average, double minimum, double maximum, long count) { }
}
