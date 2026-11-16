package com.traffic.trafficservice.repository;

import com.traffic.trafficservice.domain.RoadSegment;
import java.util.List;

public interface SegmentRepository {
    RoadSegment save(RoadSegment segment);
    List<RoadSegment> findAll();
}
