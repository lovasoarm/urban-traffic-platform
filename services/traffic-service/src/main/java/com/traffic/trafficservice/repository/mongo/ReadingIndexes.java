package com.traffic.trafficservice.repository.mongo;

import com.traffic.trafficservice.config.TrafficProperties;
import org.bson.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.GeospatialIndex;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class ReadingIndexes {
    private final MongoTemplate mongoTemplate;
    private final TrafficProperties properties;

    public ReadingIndexes(MongoTemplate mongoTemplate, TrafficProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        var indexes = mongoTemplate.indexOps(ReadingDocument.class);
        indexes.ensureIndex(new CompoundIndexDefinition(new Document("segmentId", 1).append("timestamp", 1)).named("segment_timestamp_idx"));
        indexes.ensureIndex(new CompoundIndexDefinition(new Document("vehicleId", 1).append("timestamp", 1)).named("vehicle_timestamp_idx"));
        indexes.ensureIndex(new GeospatialIndex("location").typed(GeoSpatialIndexType.GEO_2DSPHERE));
        indexes.ensureIndex(new Index().on("timestamp", Sort.Direction.ASC)
                .expire(Duration.ofDays(properties.retention().days())).named("readings_timestamp_ttl"));
    }
}
