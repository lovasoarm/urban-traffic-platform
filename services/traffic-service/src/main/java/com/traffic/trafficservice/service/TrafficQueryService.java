package com.traffic.trafficservice.service;

import com.traffic.trafficservice.domain.CongestionLevel;
import com.traffic.trafficservice.domain.RoadSegment;
import com.traffic.trafficservice.domain.SegmentTrafficState;
import com.traffic.trafficservice.exception.TrafficException;
import com.traffic.trafficservice.repository.ReadingRepository;
import com.traffic.trafficservice.repository.TrafficStateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;

@Service
public class TrafficQueryService {
    private final TrafficStateRepository states;
    private final ReadingRepository readings;
    private final SegmentCatalogService segments;

    public TrafficQueryService(TrafficStateRepository states, ReadingRepository readings,
                              SegmentCatalogService segments) {
        this.states = states;
        this.readings = readings;
        this.segments = segments;
    }

    public SegmentTrafficState getSegmentState(String id) {
        return states.findBySegmentId(id)
                .orElseThrow(() -> new TrafficException("No live state for segment: " + id, HttpStatus.NOT_FOUND));
    }

    public List<SegmentTrafficState> listByLevel(String level) {
        CongestionLevel requested = level == null ? null : parseLevel(level);
        return segments.findAll().stream().map(RoadSegment::id)
                .map(id -> states.findBySegmentId(id).orElse(null)).filter(state -> state != null)
                .filter(state -> requested == null || state.level() == requested).toList();
    }

    public List<SegmentTrafficState> topCongestion(int limit) {
        if (limit < 1 || limit > 50) {
            throw new TrafficException("n must be between 1 and 50", HttpStatus.BAD_REQUEST);
        }
        return states.findCongestionRanking(limit).stream()
                .map(id -> states.findBySegmentId(id).orElse(null)).filter(state -> state != null).toList();
    }

    public List<List<Double>> trajectory(String vehicleId, Instant from, Instant to) {
        if (!from.isBefore(to)) throw new TrafficException("from must be earlier than to", HttpStatus.BAD_REQUEST);
        List<List<Double>> coordinates = readings.findVehicleTrajectory(vehicleId, from, to).stream()
                .map(reading -> List.of(reading.location().longitude(), reading.location().latitude())).toList();
        if (coordinates.size() < 2) throw new TrafficException("At least two GPS points are required", HttpStatus.NOT_FOUND);
        return coordinates;
    }

    public List<com.traffic.trafficservice.domain.TrafficAggregate> aggregate(Instant from, Instant to) {
        if (!from.isBefore(to)) throw new TrafficException("from must be earlier than to", HttpStatus.BAD_REQUEST);
        return readings.aggregate(from, to);
    }

    private CongestionLevel parseLevel(String level) {
        try {
            return CongestionLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new TrafficException("level must be FREE, DENSE, CONGESTED or BLOCKED", HttpStatus.BAD_REQUEST);
        }
    }
}
