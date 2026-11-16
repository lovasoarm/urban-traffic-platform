package com.traffic.trafficservice.repository.mongo;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.InsertManyOptions;
import com.traffic.trafficservice.domain.GeoPoint;
import com.traffic.trafficservice.domain.TrafficAggregate;
import com.traffic.trafficservice.domain.TrafficReading;
import com.traffic.trafficservice.repository.ReadingRepository;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class MongoReadingRepository implements ReadingRepository {
    private final MongoTemplate mongoTemplate;

    public MongoReadingRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public InsertResult insertIgnoringDuplicates(List<TrafficReading> readings) {
        MongoCollection<Document> collection = mongoTemplate.getCollection("readings");
        List<Document> documents = readings.stream().map(this::toDocument).toList();
        Set<Integer> duplicateIndexes = insertUnordered(collection, documents);
        List<TrafficReading> inserted = new ArrayList<>();
        for (int index = 0; index < readings.size(); index++) {
            if (!duplicateIndexes.contains(index)) inserted.add(readings.get(index));
        }
        return new InsertResult(List.copyOf(inserted), duplicateIndexes.size());
    }

    private Set<Integer> insertUnordered(MongoCollection<Document> collection, List<Document> documents) {
        try {
            collection.insertMany(documents, new InsertManyOptions().ordered(false));
            return Set.of();
        } catch (MongoBulkWriteException exception) {
            Set<Integer> duplicates = exception.getWriteErrors().stream()
                    .filter(error -> error.getCode() == 11000).map(error -> error.getIndex()).collect(Collectors.toSet());
            if (exception.getWriteConcernError() != null || exception.getWriteErrors().isEmpty()
                    || duplicates.size() != exception.getWriteErrors().size()) {
                throw exception;
            }
            return duplicates;
        }
    }

    private Document toDocument(TrafficReading reading) {
        Document location = new Document("type", "Point")
                .append("coordinates", List.of(reading.location().longitude(), reading.location().latitude()));
        return new Document("_id", reading.readingId()).append("segmentId", reading.segmentId())
                .append("sensorId", reading.sensorId()).append("type", reading.type().name())
                .append("vehicleId", reading.vehicleId()).append("location", location)
                .append("speedKmh", reading.speedKmh()).append("timestamp", Date.from(reading.timestamp()));
    }

    @Override
    public List<TrafficReading> findVehicleTrajectory(String vehicleId, Instant from, Instant to) {
        Query query = Query.query(Criteria.where("vehicleId").is(vehicleId)
                .and("timestamp").gte(Date.from(from)).lt(Date.from(to)))
                .with(Sort.by(Sort.Direction.ASC, "timestamp"));
        return mongoTemplate.find(query, Document.class, "readings").stream().map(this::toReading).toList();
    }

    private TrafficReading toReading(Document document) {
        Document location = document.get("location", Document.class);
        List<Double> coordinates = location.getList("coordinates", Number.class).stream()
                .map(Number::doubleValue).toList();
        return new TrafficReading(document.getString("_id"), document.getString("segmentId"),
                document.getString("sensorId"), com.traffic.trafficservice.domain.ReadingType.valueOf(document.getString("type")),
                document.getString("vehicleId"), new GeoPoint(coordinates.get(1), coordinates.get(0)),
                document.getDouble("speedKmh"), document.getDate("timestamp").toInstant());
    }

    @Override
    public List<TrafficAggregate> aggregate(Instant from, Instant to) {
        List<Document> pipeline = List.of(
                new Document("$match", new Document("timestamp", new Document("$gte", Date.from(from)).append("$lt", Date.from(to)))),
                new Document("$addFields", new Document("windowStart", new Document("$dateTrunc",
                        new Document("date", "$timestamp").append("unit", "minute").append("binSize", 5).append("timezone", "UTC")))),
                new Document("$group", new Document("_id", new Document("segmentId", "$segmentId").append("windowStart", "$windowStart"))
                        .append("avgSpeedKmh", new Document("$avg", "$speedKmh"))
                        .append("minSpeedKmh", new Document("$min", "$speedKmh"))
                        .append("maxSpeedKmh", new Document("$max", "$speedKmh"))
                        .append("readingCount", new Document("$sum", 1))),
                new Document("$project", new Document("_id", 0).append("segmentId", "$_id.segmentId")
                        .append("windowStart", "$_id.windowStart").append("avgSpeedKmh", 1)
                        .append("minSpeedKmh", 1).append("maxSpeedKmh", 1).append("readingCount", 1)),
                new Document("$sort", new Document("windowStart", 1).append("segmentId", 1)));
        return mongoTemplate.getCollection("readings").aggregate(pipeline).map(this::toAggregate).into(new ArrayList<>());
    }

    private TrafficAggregate toAggregate(Document document) {
        return new TrafficAggregate(document.getString("segmentId"), document.getDate("windowStart").toInstant(),
                document.getDouble("avgSpeedKmh"), document.getDouble("minSpeedKmh"),
                document.getDouble("maxSpeedKmh"), ((Number) document.get("readingCount")).longValue());
    }
}
