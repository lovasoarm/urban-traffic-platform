package com.traffic.historyservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "history")
public record HistoryProperties(String trafficServiceUrl, int maxImportBatchSize,
                                int maxRangeDays, Collector collector) {
    public record Collector(long fixedRateMs, long initialDelayMs, int retryCount) { }
}
