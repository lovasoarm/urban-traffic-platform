package com.traffic.historyservice.service;

import com.traffic.historyservice.config.HistoryProperties;
import com.traffic.historyservice.controller.dto.HistoryPointResponse;
import com.traffic.historyservice.domain.HistoryPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class TrafficAggregationCollector {
    private static final Logger log = LoggerFactory.getLogger(TrafficAggregationCollector.class);
    private final RestClient restClient;
    private final HistoryService historyService;
    private final HistoryProperties properties;
    private final Clock clock;

    public TrafficAggregationCollector(RestClient restClient, HistoryService historyService,
                                       HistoryProperties properties, Clock clock) {
        this.restClient = restClient;
        this.historyService = historyService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedRateString = "${history.collector.fixed-rate-ms:300000}",
               initialDelayString = "${history.collector.initial-delay-ms:20000}")
    public void collectClosedWindows() {
        Instant end = closedFiveMinuteBoundary(clock.instant());
        Instant start = end.minusSeconds(900);
        List<HistoryPointResponse> aggregates = fetchWithOneRetry(start, end);
        List<HistoryPoint> points = aggregates.stream().map(value -> new HistoryPoint(value.segmentId(),
                value.windowStart(), value.averageSpeedKmh(), value.minSpeedKmh(), value.maxSpeedKmh(),
                value.readingCount())).toList();
        int stored = historyService.importPoints(points);
        log.info("Stored {} aggregate rows for [{}, {})", stored, start, end);
    }

    private List<HistoryPointResponse> fetchWithOneRetry(Instant from, Instant to) {
        RestClientException lastFailure = null;
        for (int attempt = 0; attempt <= properties.collector().retryCount(); attempt++) {
            try {
                return fetch(from, to);
            } catch (RestClientException exception) {
                lastFailure = exception;
                log.warn("Traffic aggregate request failed on attempt {}", attempt + 1, exception);
            }
        }
        log.error("Traffic aggregate collection failed after configured retries", lastFailure);
        throw lastFailure;
    }

    private List<HistoryPointResponse> fetch(Instant from, Instant to) {
        return restClient.get().uri(properties.trafficServiceUrl() + "/internal/aggregates?from={from}&to={to}", from, to)
                .retrieve().body(new ParameterizedTypeReference<>() { });
    }

    private Instant closedFiveMinuteBoundary(Instant instant) {
        long epochSeconds = instant.getEpochSecond();
        return Instant.ofEpochSecond(epochSeconds - epochSeconds % 300);
    }
}
