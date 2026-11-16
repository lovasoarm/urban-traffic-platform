package com.traffic.trafficservice.service;

import com.traffic.trafficservice.config.TrafficMetrics;
import com.traffic.trafficservice.config.TrafficProperties;
import com.traffic.trafficservice.domain.CongestionClassifier;
import com.traffic.trafficservice.domain.CongestionThresholds;
import com.traffic.trafficservice.domain.GeoPoint;
import com.traffic.trafficservice.domain.RoadSegment;
import com.traffic.trafficservice.domain.SegmentTrafficState;
import com.traffic.trafficservice.domain.TrafficReading;
import com.traffic.trafficservice.domain.TrafficAggregation;
import com.traffic.trafficservice.exception.TrafficException;
import com.traffic.trafficservice.repository.ReadingRepository;
import com.traffic.trafficservice.repository.TrafficStateRepository;
import com.traffic.trafficservice.controller.dto.ReadingRequest;
import com.traffic.trafficservice.controller.dto.ReadingsBatchRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TrafficIngestionService {
    private final ReadingRepository readings;
    private final SegmentCatalogService segments;
    private final TrafficStateRepository states;
    private final TrafficProperties properties;
    private final TrafficMetrics metrics;
    private final Clock clock;

    public TrafficIngestionService(ReadingRepository readings, SegmentCatalogService segments,
                                   TrafficStateRepository states, TrafficProperties properties,
                                   TrafficMetrics metrics, Clock clock) {
        this.readings = readings;
        this.segments = segments;
        this.states = states;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    public TrafficIngestionResult ingest(ReadingsBatchRequest batch) {
        if (batch.readings().size() > properties.ingestion().maxBatchSize()) {
            throw new TrafficException("Batch exceeds the configured maximum", HttpStatus.BAD_REQUEST);
        }
        List<TrafficReading> converted = batch.readings().stream().map(this::toDomain).toList();
        Instant now = clock.instant();
        validateFutureTimestamps(converted, now);
        Map<String, RoadSegment> catalog = segments.findAll().stream()
                .collect(Collectors.toMap(RoadSegment::id, Function.identity()));
        converted.forEach(reading -> requireSegment(catalog, reading));
        ReadingRepository.InsertResult result = readings.insertIgnoringDuplicates(converted);
        int ignoredForLiveState = updateLiveState(result.inserted(), catalog, now);
        metrics.recordIngested(result.inserted().size());
        metrics.recordIgnoredForLiveState(ignoredForLiveState);
        return new TrafficIngestionResult(result.inserted().size(), result.duplicates(), ignoredForLiveState);
    }

    private TrafficReading toDomain(ReadingRequest request) {
        return new TrafficReading(request.readingId(), request.segmentId(), request.sensorId(), request.type(),
                request.vehicleId(), new GeoPoint(request.location().latitude(), request.location().longitude()),
                request.speedKmh(), request.timestamp());
    }

    private void requireSegment(Map<String, RoadSegment> catalog, TrafficReading reading) {
        if (!catalog.containsKey(reading.segmentId())) {
            throw new TrafficException("Unknown segment: " + reading.segmentId(), HttpStatus.NOT_FOUND);
        }
    }

    private void validateFutureTimestamps(List<TrafficReading> readings, Instant now) {
        Instant latestAllowed = now.plus(properties.freshness().maxFutureSkew());
        boolean containsFutureReading = readings.stream()
                .anyMatch(reading -> reading.timestamp().isAfter(latestAllowed));
        if (containsFutureReading) {
            throw new TrafficException("Reading timestamp is too far in the future", HttpStatus.BAD_REQUEST);
        }
    }

    private int updateLiveState(List<TrafficReading> inserted, Map<String, RoadSegment> catalog, Instant now) {
        Map<String, List<TrafficReading>> bySegment = inserted.stream()
                .collect(Collectors.groupingBy(TrafficReading::segmentId));
        return bySegment.entrySet().stream()
                .mapToInt(entry -> updateSegmentState(catalog.get(entry.getKey()), entry.getValue(), now))
                .sum();
    }

    /**
     * Historical readings are always retained in MongoDB. Only fresh readings that do not
     * precede the latest reading already represented in Redis may change the live state.
     */
    private int updateSegmentState(RoadSegment segment, List<TrafficReading> batch, Instant now) {
        String segmentId = segment.id();
        SegmentTrafficState cached = states.findBySegmentId(segmentId).orElse(null);
        Instant oldestAccepted = now.minus(properties.freshness().maxAge());
        SegmentTrafficState previous = cached != null && cached.latestReadingAt().isBefore(oldestAccepted)
                ? null : cached;
        List<TrafficReading> eligible = batch.stream()
                .filter(reading -> !reading.timestamp().isBefore(oldestAccepted))
                .filter(reading -> previous == null
                        || !reading.timestamp().isBefore(previous.latestReadingAt()))
                .toList();
        int ignored = batch.size() - eligible.size();
        if (eligible.isEmpty()) return ignored;

        double batchAverage = TrafficAggregation.summarizeSpeeds(
                eligible.stream().map(TrafficReading::speedKmh).toList()).average();
        double smoothed = previous == null ? batchAverage
                : previous.smoothedSpeedKmh() * (1 - properties.ingestion().smoothingFactor())
                + batchAverage * properties.ingestion().smoothingFactor();
        long count = (previous == null ? 0 : previous.measurementCount()) + eligible.size();
        Instant latestReadingAt = eligible.stream().map(TrafficReading::timestamp)
                .max(Instant::compareTo).orElseThrow();
        if (previous != null && previous.latestReadingAt().isAfter(latestReadingAt)) {
            latestReadingAt = previous.latestReadingAt();
        }

        CongestionThresholds thresholds = new CongestionThresholds(properties.congestion().freeRatio(),
                properties.congestion().denseRatio(), properties.congestion().congestedRatio());
        SegmentTrafficState state = new SegmentTrafficState(segmentId, smoothed, segment.speedLimitKmh(),
                CongestionClassifier.classify(smoothed, segment.speedLimitKmh(), thresholds), now,
                latestReadingAt, count);
        states.save(state, properties.cache().ttl().toSeconds());
        metrics.recordSegment(state);
        return ignored;
    }

}
