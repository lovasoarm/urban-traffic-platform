package com.traffic.trafficservice.controller.dto;

import java.time.Instant;

public record TrafficAggregateResponse(String segmentId, Instant windowStart, double averageSpeedKmh,
                                       double minSpeedKmh, double maxSpeedKmh, long readingCount) { }
