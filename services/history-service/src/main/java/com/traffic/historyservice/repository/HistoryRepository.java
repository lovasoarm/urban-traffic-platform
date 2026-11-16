package com.traffic.historyservice.repository;

import com.traffic.historyservice.domain.HistoryPoint;
import java.time.Instant;
import java.util.List;

public interface HistoryRepository {
    void upsertAll(List<HistoryPoint> points);
    List<HistoryPoint> findBySegmentAndRange(String segmentId, Instant from, Instant to, int limitPerDay);
}
