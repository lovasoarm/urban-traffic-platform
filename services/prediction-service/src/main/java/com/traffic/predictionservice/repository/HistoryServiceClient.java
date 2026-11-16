package com.traffic.predictionservice.repository;

import com.traffic.predictionservice.config.PredictionProperties;
import com.traffic.predictionservice.domain.ForecastSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
public class HistoryServiceClient implements HistoryClient {
    private static final Logger log = LoggerFactory.getLogger(HistoryServiceClient.class);
    private final RestClient restClient;
    private final PredictionProperties properties;

    public HistoryServiceClient(RestClient restClient, PredictionProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public List<ForecastSample> loadSamples(String segmentId, Instant from, Instant to) {
        List<HistoryPointResponse> response = fetchWithOneRetry(segmentId, from, to);
        return response.stream().map(point -> new ForecastSample(point.windowStart(), point.averageSpeedKmh())).toList();
    }

    private List<HistoryPointResponse> fetchWithOneRetry(String segmentId, Instant from, Instant to) {
        RestClientException lastFailure = null;
        for (int attempt = 0; attempt <= properties.retryCount(); attempt++) {
            try {
                return fetchAllPages(segmentId, from, to);
            } catch (RestClientException exception) {
                lastFailure = exception;
                log.warn("History API request failed for {} on attempt {}", segmentId, attempt + 1, exception);
            }
        }
        throw lastFailure;
    }

    private List<HistoryPointResponse> fetchAllPages(String segmentId, Instant from, Instant to) {
        List<HistoryPointResponse> all = new ArrayList<>();
        int page = 0;
        boolean hasNext;
        do {
            HistoryPageResponse response = restClient.get().uri(properties.historyServiceUrl()
                            + "/api/v1/history/segments/{id}?from={from}&to={to}&granularity=5m&page={page}&size={size}",
                    segmentId, from, to, page, properties.historyPageSize())
                    .retrieve().body(HistoryPageResponse.class);
            all.addAll(response.content());
            hasNext = response.hasNext();
            page++;
        } while (hasNext);
        return all;
    }

    public record HistoryPageResponse(List<HistoryPointResponse> content, int page, int size,
                                      long totalElements, boolean hasNext) { }
    public record HistoryPointResponse(String segmentId, Instant windowStart, double averageSpeedKmh,
                                       double minSpeedKmh, double maxSpeedKmh, long readingCount) { }
}
