package com.traffic.predictionservice.service;

import com.traffic.predictionservice.config.PredictionProperties;
import com.traffic.predictionservice.domain.ForecastModelSnapshot;
import com.traffic.predictionservice.domain.InsufficientHistoryException;
import com.traffic.predictionservice.domain.ModelFit;
import com.traffic.predictionservice.domain.SeasonalForecastModel;
import com.traffic.predictionservice.repository.HistoryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PredictionTrainingService {
    private static final Logger log = LoggerFactory.getLogger(PredictionTrainingService.class);
    private final HistoryClient historyClient;
    private final PredictionProperties properties;
    private final PredictionMetrics metrics;
    private final Clock clock;
    private final Map<String, ForecastModelSnapshot> models = new ConcurrentHashMap<>();

    public PredictionTrainingService(HistoryClient historyClient, PredictionProperties properties,
                                     PredictionMetrics metrics, Clock clock) {
        this.historyClient = historyClient;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void trainAtStartup() { trainAll(); }

    @Scheduled(fixedRateString = "${prediction.training-fixed-rate-ms:3600000}", initialDelayString = "${prediction.training-fixed-rate-ms:3600000}")
    public void trainHourly() { trainAll(); }

    @Scheduled(fixedDelayString = "${prediction.retry-fixed-delay-ms:30000}", initialDelayString = "${prediction.retry-fixed-delay-ms:30000}")
    public void retryUntilFirstModelIsReady() {
        var missing = properties.segments().stream().filter(segmentId -> !models.containsKey(segmentId)).toList();
        if (!missing.isEmpty()) trainSegments(missing);
    }

    public void trainAll() { trainSegments(properties.segments()); }

    private void trainSegments(java.util.List<String> segmentIds) {
        Instant to = clock.instant();
        Instant from = to.minus(Duration.ofDays(properties.lookbackDays()));
        segmentIds.forEach(segmentId -> trainSegment(segmentId, from, to));
        updateGlobalMae();
    }

    private void trainSegment(String segmentId, Instant from, Instant to) {
        try {
            var samples = historyClient.loadSamples(segmentId, from, to);
            ModelFit fit = SeasonalForecastModel.train(samples, properties.minimumSamples());
            models.put(segmentId, snapshot(segmentId, fit));
            log.info("Trained traffic model for {}: MAE {}, baseline MAE {}, {} samples",
                    segmentId, fit.modelMae(), fit.baselineMae(), samples.size());
        } catch (InsufficientHistoryException exception) {
            log.warn("Prediction model for {} is not ready yet: {}", segmentId, exception.getMessage());
        } catch (RestClientException exception) {
            log.warn("History service is temporarily unavailable for {}; training will retry", segmentId, exception);
        }
    }

    private ForecastModelSnapshot snapshot(String segmentId, ModelFit fit) {
        return new ForecastModelSnapshot(segmentId, fit.profile(), fit.latestSpeedKmh(), fit.modelMae(),
                fit.baselineMae(), clock.instant(), fit.trainingSamples(), fit.evaluationSamples());
    }

    private void updateGlobalMae() {
        if (!models.isEmpty()) metrics.updateMae(models.values().stream().mapToDouble(ForecastModelSnapshot::mae).average().orElse(0));
    }

    public ForecastModelSnapshot modelFor(String segmentId) {
        ForecastModelSnapshot model = models.get(segmentId);
        if (model == null) throw new com.traffic.predictionservice.exception.PredictionException(
                "Prediction model is not trained yet for segment " + segmentId,
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        return model;
    }

    public Map<String, ForecastModelSnapshot> models() { return Map.copyOf(models); }
}
