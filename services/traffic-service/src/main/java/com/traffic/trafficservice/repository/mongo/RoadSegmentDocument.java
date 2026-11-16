package com.traffic.trafficservice.repository.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("road_segments")
public record RoadSegmentDocument(@Id String id, String name, double speedLimitKmh) { }
