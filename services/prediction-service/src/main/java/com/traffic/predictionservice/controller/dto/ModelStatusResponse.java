package com.traffic.predictionservice.controller.dto;

import java.time.Instant;
import java.util.List;

public record ModelStatusResponse(boolean ready, double averageMae, Instant lastTrainingAt,
                                  List<SegmentModelStatus> segments) {
    public record SegmentModelStatus(String segmentId, double mae, double baselineMae,
                                     int trainingSamples, int evaluationSamples, Instant trainedAt) { }
}
