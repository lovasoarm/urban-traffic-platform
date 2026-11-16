package com.traffic.simulator.service;

import com.traffic.simulator.config.SimulatorProperties;
import com.traffic.simulator.domain.TrafficPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Component
public class TrafficSimulator implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(TrafficSimulator.class);
    private static final int WINDOWS_PER_DAY = 288;
    private static final int IMPORT_BATCH_LIMIT = 5000;
    private final RestClient restClient;
    private final SimulatorProperties properties;
    private final Clock clock;
    private final Random random;
    private final SimulatorMetrics metrics;
    private final RestCallExecutor restCalls;
    private final List<Segment> segments = new ArrayList<>();
    private volatile boolean seedComplete;

    public TrafficSimulator(RestClient restClient, SimulatorProperties properties, Clock clock,
                            SimulatorMetrics metrics, RestCallExecutor restCalls) {
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
        this.metrics = metrics;
        this.restCalls = restCalls;
        this.random = new Random(properties.randomSeed());
    }

    @Override
    public void run(String... args) {
        createSegments();
        seedHistory();
        seedComplete = true;
        log.info("Simulator seeded {} road segments across {} days", segments.size(), properties.seedDays());
    }

    private void createSegments() {
        for (int index = 0; index < properties.segmentCount(); index++) {
            Segment segment = new Segment(segmentId(index), "Urban avenue " + (index + 1), 50 + (index % 3) * 10);
            restCalls.execute("create segment " + segment.id(), () -> restClient.post()
                    .uri(properties.gatewayUrl() + "/api/v1/segments").body(segment)
                    .retrieve().toBodilessEntity());
            segments.add(segment);
        }
    }

    private void seedHistory() {
        Instant end = floorFiveMinutes(clock.instant());
        Instant start = end.minus(properties.seedDays(), ChronoUnit.DAYS);
        for (int day = 0; day < properties.seedDays(); day++) {
            Instant dayStart = start.plus(day, ChronoUnit.DAYS);
            List<HistoryPoint> daily = createDailyHistory(dayStart);
            for (int offset = 0; offset < daily.size(); offset += IMPORT_BATCH_LIMIT) {
                List<HistoryPoint> batch = daily.subList(offset, Math.min(offset + IMPORT_BATCH_LIMIT, daily.size()));
                HistoryImportRequest request = new HistoryImportRequest(batch);
                restCalls.execute("import history batch", () -> restClient.post()
                        .uri(properties.gatewayUrl() + "/api/v1/history/import").body(request)
                        .retrieve().toBodilessEntity());
                metrics.recordSeededRows(batch.size());
            }
        }
    }

    private List<HistoryPoint> createDailyHistory(Instant dayStart) {
        List<HistoryPoint> points = new ArrayList<>(WINDOWS_PER_DAY * segments.size());
        for (int window = 0; window < WINDOWS_PER_DAY; window++) {
            Instant timestamp = dayStart.plus(window * 300L, ChronoUnit.SECONDS);
            for (int index = 0; index < segments.size(); index++) {
                double limit = segments.get(index).speedLimitKmh();
                boolean incident = random.nextDouble() < 0.006;
                double average = TrafficPattern.speed(timestamp, limit, index, random.nextDouble() * 2.0 - 1.0, incident);
                points.add(new HistoryPoint(segments.get(index).id(), timestamp, average,
                        Math.max(0, average - 3.5), Math.min(limit, average + 3.5), 6));
            }
        }
        return points;
    }

    @Scheduled(fixedRateString = "${simulator.interval-ms:5000}", initialDelayString = "${simulator.interval-ms:5000}")
    public void sendLiveReadings() {
        if (!seedComplete || segments.isEmpty()) return;
        List<Reading> readings = new ArrayList<>(segments.size() * properties.measurementsPerSegment());
        Instant timestamp = clock.instant();
        for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
            Segment segment = segments.get(segmentIndex);
            for (int sample = 0; sample < properties.measurementsPerSegment(); sample++) {
                readings.add(createReading(segment, segmentIndex, sample, timestamp));
            }
        }
        if (readings.size() > 500) throw new IllegalStateException("Simulator batch exceeds traffic-service limit of 500");
        IngestionResponse response = restCalls.execute("send live readings", () -> restClient.post()
                .uri(properties.gatewayUrl() + "/api/v1/readings")
                .body(new ReadingBatch(readings)).retrieve().body(IngestionResponse.class));
        if (response == null) throw new IllegalStateException("Traffic ingestion returned an empty response");
        metrics.recordLiveBatch();
        log.info("Sent live batch: inserted={}, duplicates={}, ignoredForLiveState={}",
                response.inserted(), response.duplicates(), response.ignoredForLiveState());
    }

    private Reading createReading(Segment segment, int segmentIndex, int sample, Instant timestamp) {
        boolean gpsProbe = sample % 2 == 1;
        double speed = TrafficPattern.speed(timestamp, segment.speedLimitKmh(), segmentIndex,
                random.nextDouble() * 5 - 2.5, random.nextDouble() < 0.01);
        double latitude = properties.centerLatitude() + segmentIndex * 0.0015 + random.nextDouble() * 0.0004;
        double longitude = properties.centerLongitude() + segmentIndex * 0.0018 + random.nextDouble() * 0.0004;
        return new Reading(UUID.randomUUID().toString(), segment.id(), "sensor-" + segmentIndex + "-" + sample,
                gpsProbe ? "GPS_PROBE" : "FIXED", gpsProbe ? "vehicle-" + random.nextInt(250) : null,
                new Location(latitude, longitude), speed, timestamp);
    }

    private Instant floorFiveMinutes(Instant instant) {
        long seconds = instant.getEpochSecond();
        return Instant.ofEpochSecond(seconds - seconds % 300).truncatedTo(ChronoUnit.MINUTES);
    }

    private String segmentId(int index) { return "segment-%02d".formatted(index + 1); }

    public record Segment(String id, String name, double speedLimitKmh) { }
    public record HistoryPoint(String segmentId, Instant windowStart, double averageSpeedKmh,
                                double minSpeedKmh, double maxSpeedKmh, long readingCount) { }
    public record HistoryImportRequest(List<HistoryPoint> points) { }
    public record Location(double latitude, double longitude) { }
    public record Reading(String readingId, String segmentId, String sensorId, String type,
                           String vehicleId, Location location, double speedKmh, Instant timestamp) { }
    public record ReadingBatch(List<Reading> readings) { }
    public record IngestionResponse(int inserted, int duplicates, int ignoredForLiveState) { }
}
