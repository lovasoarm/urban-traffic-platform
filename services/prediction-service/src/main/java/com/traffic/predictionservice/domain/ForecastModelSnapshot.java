package com.traffic.predictionservice.domain;

import java.time.Instant;

public record ForecastModelSnapshot(String segmentId, SeasonalProfile profile, double latestSpeedKmh,
                                    double mae, double baselineMae, Instant trainedAt,
                                    int trainingSamples, int evaluationSamples) { }
