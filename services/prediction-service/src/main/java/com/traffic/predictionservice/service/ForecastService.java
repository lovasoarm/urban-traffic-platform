package com.traffic.predictionservice.service;

import com.traffic.predictionservice.domain.ForecastModelSnapshot;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class ForecastService {
    private final PredictionTrainingService trainingService;
    private final Clock clock;

    public ForecastService(PredictionTrainingService trainingService, Clock clock) {
        this.trainingService = trainingService;
        this.clock = clock;
    }

    public Forecast forecast(String segmentId, int horizonMinutes) {
        ForecastModelSnapshot model = trainingService.modelFor(segmentId);
        Instant generatedAt = clock.instant();
        double speed = model.profile().forecast(generatedAt.plusSeconds(horizonMinutes * 60L),
                model.latestSpeedKmh(), horizonMinutes);
        return new Forecast(segmentId, horizonMinutes, speed, generatedAt.plusSeconds(horizonMinutes * 60L),
                model.mae(), model.trainedAt());
    }

    public List<Forecast> hotspots(int horizonMinutes) {
        if (trainingService.models().isEmpty()) {
            throw new com.traffic.predictionservice.exception.PredictionException(
                    "Prediction models are not trained yet", org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        }
        return trainingService.models().keySet().stream().map(id -> forecast(id, horizonMinutes))
                .sorted(Comparator.comparingDouble(Forecast::predictedSpeedKmh)).toList();
    }

    public record Forecast(String segmentId, int horizonMinutes, double predictedSpeedKmh,
                           Instant forecastFor, double modelMae, Instant modelTrainedAt) { }
}
