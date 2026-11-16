package com.traffic.trafficservice.repository.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import java.time.Instant;

@Document("readings")
@CompoundIndexes({
        @CompoundIndex(name = "segment_timestamp_idx", def = "{ 'segmentId': 1, 'timestamp': 1 }"),
        @CompoundIndex(name = "vehicle_timestamp_idx", def = "{ 'vehicleId': 1, 'timestamp': 1 }")
})
public record ReadingDocument(@Id String readingId, String segmentId, String sensorId, String type,
                              String vehicleId,
                              @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE) GeoJsonPoint location,
                              double speedKmh, Instant timestamp) { }
