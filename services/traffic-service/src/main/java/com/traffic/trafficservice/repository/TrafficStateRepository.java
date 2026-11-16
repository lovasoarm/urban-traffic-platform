package com.traffic.trafficservice.repository;

import com.traffic.trafficservice.domain.SegmentTrafficState;
import java.util.List;
import java.util.Optional;

public interface TrafficStateRepository {
    void save(SegmentTrafficState state, long ttlSeconds);
    Optional<SegmentTrafficState> findBySegmentId(String segmentId);
    List<String> findCongestionRanking(int limit);
}
