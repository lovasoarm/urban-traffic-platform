package com.traffic.predictionservice.domain;

public record ModelFit(SeasonalProfile profile, double modelMae, double baselineMae,
                       double latestSpeedKmh, int trainingSamples, int evaluationSamples) { }
