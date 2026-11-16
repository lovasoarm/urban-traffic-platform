package com.traffic.trafficservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "traffic")
public record TrafficProperties(Ingestion ingestion, Cache cache, Congestion congestion,
                                Retention retention, Freshness freshness) {
    public record Ingestion(int maxBatchSize, double smoothingFactor) { }
    public record Cache(Duration ttl) { }
    public record Congestion(double freeRatio, double denseRatio, double congestedRatio) { }
    public record Retention(int days) { }
    public record Freshness(Duration maxAge, Duration maxFutureSkew) { }
}
