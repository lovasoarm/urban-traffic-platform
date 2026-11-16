package com.traffic.trafficservice.service;

import com.traffic.trafficservice.config.TrafficMetrics;
import com.traffic.trafficservice.config.TrafficProperties;
import com.traffic.trafficservice.domain.CongestionLevel;
import com.traffic.trafficservice.domain.GeoPoint;
import com.traffic.trafficservice.domain.ReadingType;
import com.traffic.trafficservice.domain.RoadSegment;
import com.traffic.trafficservice.domain.SegmentTrafficState;
import com.traffic.trafficservice.domain.TrafficReading;
import com.traffic.trafficservice.exception.TrafficException;
import com.traffic.trafficservice.repository.ReadingRepository;
import com.traffic.trafficservice.repository.TrafficStateRepository;
import com.traffic.trafficservice.controller.dto.ReadingRequest;
import com.traffic.trafficservice.controller.dto.ReadingsBatchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrafficIngestionServiceTest {
    @Mock private ReadingRepository readings;
    @Mock private SegmentCatalogService segments;
    @Mock private TrafficStateRepository states;
    @Mock private TrafficMetrics metrics;
    private TrafficIngestionService service;
    private final Instant now = Instant.parse("2026-11-14T10:00:00Z");

    @BeforeEach
    void setUp() {
        var properties = new TrafficProperties(new TrafficProperties.Ingestion(500, 0.5),
                new TrafficProperties.Cache(Duration.ofMinutes(10)),
                new TrafficProperties.Congestion(0.70, 0.40, 0.15), new TrafficProperties.Retention(7),
                new TrafficProperties.Freshness(Duration.ofMinutes(10), Duration.ofMinutes(1)));
        service = new TrafficIngestionService(readings, segments, states, properties, metrics,
                Clock.fixed(now, ZoneOffset.UTC));
        when(segments.findAll()).thenReturn(List.of(
                new RoadSegment("segment-01", "North avenue", 60),
                new RoadSegment("segment-02", "South avenue", 50)));
        when(states.findBySegmentId(any())).thenReturn(Optional.empty());
    }

    @Test
    void writesOneLiveStatePerSegmentAndIgnoresDuplicateReadings() {
        var first = request("reading-1", "segment-01", 42, now);
        var second = request("reading-2", "segment-01", 30, now);
        var third = request("reading-3", "segment-02", 10, now);
        List<TrafficReading> inserted = List.of(toDomain(first), toDomain(second), toDomain(third));
        when(readings.insertIgnoringDuplicates(any())).thenReturn(new ReadingRepository.InsertResult(inserted, 1));

        var result = service.ingest(new ReadingsBatchRequest(List.of(first, second, third,
                request("duplicate", "segment-01", 20, now))));

        assertThat(result.inserted()).isEqualTo(3);
        assertThat(result.duplicates()).isEqualTo(1);
        assertThat(result.ignoredForLiveState()).isZero();
        ArgumentCaptor<SegmentTrafficState> statesCaptor = ArgumentCaptor.forClass(SegmentTrafficState.class);
        verify(states, times(2)).save(statesCaptor.capture(), eq(600L));
        assertThat(statesCaptor.getAllValues()).extracting(SegmentTrafficState::segmentId)
                .containsExactlyInAnyOrder("segment-01", "segment-02");
        SegmentTrafficState north = statesCaptor.getAllValues().stream()
                .filter(state -> state.segmentId().equals("segment-01")).findFirst().orElseThrow();
        assertThat(north.smoothedSpeedKmh()).isEqualTo(36.0);
        assertThat(north.level()).isEqualTo(CongestionLevel.DENSE);
        assertThat(north.updatedAt()).isEqualTo(now);
        assertThat(north.latestReadingAt()).isEqualTo(now);
        assertThat(north.measurementCount()).isEqualTo(2);
        verify(segments, times(1)).findAll();
        verify(metrics).recordIngested(3);
        verify(metrics).recordIgnoredForLiveState(0);
    }

    @Test
    void retainsOldReadingsButDoesNotUseThemForLiveState() {
        var oldReading = request("old-reading", "segment-01", 12, now.minus(Duration.ofMinutes(11)));
        when(readings.insertIgnoringDuplicates(any())).thenReturn(
                new ReadingRepository.InsertResult(List.of(toDomain(oldReading)), 0));

        var result = service.ingest(new ReadingsBatchRequest(List.of(oldReading)));

        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.ignoredForLiveState()).isEqualTo(1);
        verify(readings).insertIgnoringDuplicates(any());
        verify(states, never()).save(any(), anyLong());
        verify(metrics).recordIgnoredForLiveState(1);
    }

    @Test
    void doesNotLetAnOutOfOrderReadingReplaceTheCurrentState() {
        Instant latestReadingAt = now.minusSeconds(5);
        var currentState = new SegmentTrafficState("segment-01", 35.0, 60.0, CongestionLevel.DENSE,
                now, latestReadingAt, 8);
        when(states.findBySegmentId("segment-01")).thenReturn(Optional.of(currentState));
        var lateReading = request("late-reading", "segment-01", 5, now.minusSeconds(10));
        when(readings.insertIgnoringDuplicates(any())).thenReturn(
                new ReadingRepository.InsertResult(List.of(toDomain(lateReading)), 0));

        var result = service.ingest(new ReadingsBatchRequest(List.of(lateReading)));

        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.ignoredForLiveState()).isEqualTo(1);
        verify(states, never()).save(any(), anyLong());
    }

    @Test
    void rejectsTimestampsBeyondTheAllowedFutureSkewBeforePersisting() {
        var futureReading = request("future-reading", "segment-01", 40, now.plus(Duration.ofMinutes(2)));

        assertThatThrownBy(() -> service.ingest(new ReadingsBatchRequest(List.of(futureReading))))
                .isInstanceOf(TrafficException.class)
                .satisfies(exception -> assertThat(((TrafficException) exception).status())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(readings, never()).insertIgnoringDuplicates(any());
    }

    private ReadingRequest request(String id, String segmentId, double speed, Instant timestamp) {
        return new ReadingRequest(id, segmentId, "sensor-1", ReadingType.FIXED, null,
                new ReadingRequest.LocationRequest(-18.9, 47.5), speed, timestamp);
    }

    private TrafficReading toDomain(ReadingRequest request) {
        return new TrafficReading(request.readingId(), request.segmentId(), request.sensorId(), request.type(),
                request.vehicleId(), new GeoPoint(request.location().latitude(), request.location().longitude()),
                request.speedKmh(), request.timestamp());
    }
}
