package com.traffic.predictionservice.repository;

import com.traffic.predictionservice.domain.ForecastSample;
import java.time.Instant;
import java.util.List;

public interface HistoryClient {
    List<ForecastSample> loadSamples(String segmentId, Instant from, Instant to);
}
