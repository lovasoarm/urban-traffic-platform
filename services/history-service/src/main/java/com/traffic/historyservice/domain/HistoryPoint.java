package com.traffic.historyservice.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public record HistoryPoint(String segmentId, Instant windowStart, double averageSpeedKmh,
                           double minSpeedKmh, double maxSpeedKmh, long readingCount) {
    public LocalDate day() { return windowStart.atZone(ZoneOffset.UTC).toLocalDate(); }
}
