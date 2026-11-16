package com.traffic.trafficservice.domain;

import java.time.Instant;

public record TrafficAggregate(String segmentId, Instant windowStart, double averageSpeedKmh,
                               double minSpeedKmh, double maxSpeedKmh, long readingCount) { }
