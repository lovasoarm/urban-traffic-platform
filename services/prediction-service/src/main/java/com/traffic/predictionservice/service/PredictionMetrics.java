package com.traffic.predictionservice.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class PredictionMetrics {
    private final AtomicReference<Double> mae = new AtomicReference<>(0.0);

    public PredictionMetrics(MeterRegistry registry) {
        registry.gauge("traffic_forecast_mae", mae, AtomicReference::get);
    }

    public void updateMae(double value) { mae.set(value); }
}
