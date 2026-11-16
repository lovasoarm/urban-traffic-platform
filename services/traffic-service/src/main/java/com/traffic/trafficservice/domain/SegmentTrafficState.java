package com.traffic.trafficservice.domain;

import java.time.Instant;

public record SegmentTrafficState(String segmentId, double smoothedSpeedKmh, double speedLimitKmh,
                                  CongestionLevel level, Instant updatedAt, Instant latestReadingAt,
                                  long measurementCount) { }
