package com.traffic.trafficservice.repository.mongo;

import com.traffic.trafficservice.domain.RoadSegment;
import com.traffic.trafficservice.repository.SegmentRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class MongoSegmentRepository implements SegmentRepository {
    private final MongoTemplate mongoTemplate;

    public MongoSegmentRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public RoadSegment save(RoadSegment segment) {
        RoadSegmentDocument saved = mongoTemplate.save(new RoadSegmentDocument(
                segment.id(), segment.name(), segment.speedLimitKmh()));
        return new RoadSegment(saved.id(), saved.name(), saved.speedLimitKmh());
    }

    @Override
    public List<RoadSegment> findAll() {
        return mongoTemplate.findAll(RoadSegmentDocument.class).stream()
                .map(document -> new RoadSegment(document.id(), document.name(), document.speedLimitKmh()))
                .toList();
    }
}
