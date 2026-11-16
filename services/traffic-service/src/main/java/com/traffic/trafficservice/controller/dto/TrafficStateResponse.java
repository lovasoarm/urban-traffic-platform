package com.traffic.trafficservice.controller.dto;

import com.traffic.trafficservice.domain.CongestionLevel;
import java.time.Instant;

public record TrafficStateResponse(String segmentId, double smoothedSpeedKmh, double speedLimitKmh,
                                   CongestionLevel level, Instant updatedAt, long measurementCount) { }
