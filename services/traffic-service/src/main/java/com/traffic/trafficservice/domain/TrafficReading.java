package com.traffic.trafficservice.domain;

import java.time.Instant;

public record TrafficReading(String readingId, String segmentId, String sensorId, ReadingType type,
                             String vehicleId, GeoPoint location, double speedKmh, Instant timestamp) { }
