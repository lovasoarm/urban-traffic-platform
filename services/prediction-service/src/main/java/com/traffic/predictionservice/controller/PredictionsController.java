package com.traffic.predictionservice.controller;

import com.traffic.predictionservice.controller.dto.ForecastResponse;
import com.traffic.predictionservice.controller.dto.ModelStatusResponse;
import com.traffic.predictionservice.domain.ForecastModelSnapshot;
import com.traffic.predictionservice.service.ForecastService;
import com.traffic.predictionservice.service.PredictionTrainingService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Comparator;

@RestController
@Validated
@RequestMapping("/api/v1/predictions")
public class PredictionsController {
    private final ForecastService forecasts;
    private final PredictionTrainingService training;

    public PredictionsController(ForecastService forecasts, PredictionTrainingService training) {
        this.forecasts = forecasts;
        this.training = training;
    }

    @GetMapping("/segments/{id}")
    public ForecastResponse segment(@PathVariable String id,
            @RequestParam(defaultValue = "15") @Min(1) @Max(120) int horizonMinutes) {
        return response(forecasts.forecast(id, horizonMinutes));
    }

    @GetMapping("/hotspots")
    public java.util.List<ForecastResponse> hotspots(
            @RequestParam(defaultValue = "30") @Min(1) @Max(120) int horizonMinutes) {
        return forecasts.hotspots(horizonMinutes).stream().map(this::response).toList();
    }

    @GetMapping("/model")
    public ModelStatusResponse modelStatus() {
        var models = training.models();
        double mae = models.values().stream().mapToDouble(ForecastModelSnapshot::mae).average().orElse(0);
        var lastTraining = models.values().stream().map(ForecastModelSnapshot::trainedAt).max(Comparator.naturalOrder()).orElse(null);
        var segments = models.values().stream().sorted(Comparator.comparing(ForecastModelSnapshot::segmentId))
                .map(model -> new ModelStatusResponse.SegmentModelStatus(model.segmentId(), model.mae(),
                        model.baselineMae(), model.trainingSamples(), model.evaluationSamples(), model.trainedAt())).toList();
        return new ModelStatusResponse(!models.isEmpty(), mae, lastTraining, segments);
    }

    private ForecastResponse response(ForecastService.Forecast forecast) {
        return new ForecastResponse(forecast.segmentId(), forecast.horizonMinutes(), forecast.predictedSpeedKmh(),
                forecast.forecastFor(), forecast.modelMae(), forecast.modelTrainedAt());
    }
}
