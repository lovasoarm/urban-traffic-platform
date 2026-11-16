package com.traffic.predictionservice.controller.dto;

import java.time.Instant;

public record ForecastResponse(String segmentId, int horizonMinutes, double predictedSpeedKmh,
                               Instant forecastFor, double modelMae, Instant modelTrainedAt) { }
