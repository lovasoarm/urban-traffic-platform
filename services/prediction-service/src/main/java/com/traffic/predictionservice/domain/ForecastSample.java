package com.traffic.predictionservice.domain;

import java.time.Instant;

public record ForecastSample(Instant timestamp, double speedKmh) { }
