package com.traffic.historyservice.controller.dto;

import java.time.Instant;

public record HistoryPointResponse(String segmentId, Instant windowStart, double averageSpeedKmh,
                                   double minSpeedKmh, double maxSpeedKmh, long readingCount) { }
