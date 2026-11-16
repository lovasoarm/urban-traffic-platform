package com.traffic.trafficservice.service;

import com.traffic.trafficservice.domain.RoadSegment;
import com.traffic.trafficservice.exception.TrafficException;
import com.traffic.trafficservice.repository.SegmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class SegmentCatalogService {
    private final SegmentRepository repository;

    public SegmentCatalogService(SegmentRepository repository) {
        this.repository = repository;
    }

    public RoadSegment save(RoadSegment segment) {
        if (segment.speedLimitKmh() <= 0) throw new TrafficException("speedLimitKmh must be positive", HttpStatus.BAD_REQUEST);
        return repository.save(segment);
    }


    public List<RoadSegment> findAll() {
        return repository.findAll();
    }
}
