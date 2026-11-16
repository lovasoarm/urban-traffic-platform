package com.traffic.trafficservice.config;

import com.traffic.trafficservice.domain.CongestionLevel;
import com.traffic.trafficservice.domain.SegmentTrafficState;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import io.micrometer.core.instrument.Tags;

@Component
public class TrafficMetrics {
    private final MeterRegistry registry;
    private final Clock clock;
    private final long cacheTtlSeconds;
    private final Map<String, AtomicReference<Double>> speeds = new ConcurrentHashMap<>();
    private final Map<String, Instant> speedUpdatedAt = new ConcurrentHashMap<>();
    private final Map<String, SegmentTrafficState> liveStates = new ConcurrentHashMap<>();

    public TrafficMetrics(MeterRegistry registry, TrafficProperties properties, Clock clock) {
        this.registry = registry;
        this.clock = clock;
        this.cacheTtlSeconds = properties.cache().ttl().toSeconds();
        registry.gauge("traffic_segments_congested", this, TrafficMetrics::activeCongestedCount);
    }

    public void recordIngested(long count) {
        registry.counter("traffic_readings_ingested_total").increment(count);
    }

    public void recordIgnoredForLiveState(long count) {
        if (count > 0) registry.counter("traffic_readings_ignored_for_live_state_total").increment(count);
    }

    public void recordSegment(SegmentTrafficState state) {
        AtomicReference<Double> speed = speeds.computeIfAbsent(state.segmentId(), id -> {
            AtomicReference<Double> value = new AtomicReference<>(state.smoothedSpeedKmh());
           registry.gauge(
    "traffic_avg_speed_kmh",
    Tags.of("segment", id),
    this,
    metrics -> metrics.activeSpeed(id)
);
            return value;
        });
        speed.set(state.smoothedSpeedKmh());
        speedUpdatedAt.put(state.segmentId(), state.updatedAt());
        liveStates.put(state.segmentId(), state);
    }

    private double activeSpeed(String segmentId) {
        Instant updatedAt = speedUpdatedAt.get(segmentId);
        if (updatedAt == null || !updatedAt.isAfter(clock.instant().minusSeconds(cacheTtlSeconds))) {
            return Double.NaN;
        }
        return speeds.get(segmentId).get();
    }

    private double activeCongestedCount() {
        Instant oldestActive = clock.instant().minusSeconds(cacheTtlSeconds);
        return liveStates.values().stream().filter(state -> state.updatedAt().isAfter(oldestActive))
                .filter(state -> state.level() == CongestionLevel.CONGESTED || state.level() == CongestionLevel.BLOCKED)
                .count();
    }
}
