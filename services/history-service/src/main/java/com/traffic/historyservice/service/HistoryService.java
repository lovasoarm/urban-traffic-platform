package com.traffic.historyservice.service;

import com.traffic.historyservice.config.HistoryProperties;
import com.traffic.historyservice.controller.dto.HistoryPageResponse;
import com.traffic.historyservice.controller.dto.HistoryPointResponse;
import com.traffic.historyservice.domain.HistoryGranularity;
import com.traffic.historyservice.domain.HistoryPoint;
import com.traffic.historyservice.exception.HistoryException;
import com.traffic.historyservice.repository.HistoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class HistoryService {
    private final HistoryRepository repository;
    private final HistoryProperties properties;

    public HistoryService(HistoryRepository repository, HistoryProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public int importPoints(List<HistoryPoint> points) {
        if (points.size() > properties.maxImportBatchSize()) {
            throw new HistoryException("Import batch exceeds the configured maximum", HttpStatus.BAD_REQUEST);
        }
        points.forEach(this::validatePoint);
        repository.upsertAll(points);
        return points.size();
    }

    public HistoryPageResponse getHistory(String segmentId, Instant from, Instant to,
                                          HistoryGranularity granularity, int page, int size) {
        validateRange(from, to, page, size);
        List<HistoryPoint> raw = repository.findBySegmentAndRange(segmentId, from, to, 5000);
        List<HistoryPoint> grouped = aggregateGranularity(raw, granularity);
        int start = (int) Math.min((long) page * size, grouped.size());
        int end = Math.min(start + size, grouped.size());
        List<HistoryPointResponse> content = grouped.subList(start, end).stream().map(this::response).toList();
        return new HistoryPageResponse(content, page, size, grouped.size(), end < grouped.size());
    }

    private void validatePoint(HistoryPoint point) {
        if (point.averageSpeedKmh() < 0 || point.minSpeedKmh() < 0 || point.maxSpeedKmh() < point.minSpeedKmh()
                || point.averageSpeedKmh() < point.minSpeedKmh() || point.averageSpeedKmh() > point.maxSpeedKmh()) {
            throw new HistoryException("Invalid speed statistics for segment " + point.segmentId(), HttpStatus.BAD_REQUEST);
        }
    }

    private void validateRange(Instant from, Instant to, int page, int size) {
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(Duration.ofDays(properties.maxRangeDays())) > 0) {
            throw new HistoryException("Date range must be positive and at most " + properties.maxRangeDays() + " days", HttpStatus.BAD_REQUEST);
        }
        if (page < 0 || size < 1 || size > 5000) throw new HistoryException("Invalid pagination parameters", HttpStatus.BAD_REQUEST);
    }

    private List<HistoryPoint> aggregateGranularity(List<HistoryPoint> points, HistoryGranularity granularity) {
        if (granularity == HistoryGranularity.FIVE_MINUTES) return points;
        ChronoUnit unit = granularity == HistoryGranularity.ONE_HOUR ? ChronoUnit.HOURS : ChronoUnit.DAYS;
        Map<Instant, List<HistoryPoint>> buckets = points.stream().collect(Collectors.groupingBy(
                point -> point.windowStart().truncatedTo(unit)));
        return buckets.entrySet().stream().map(entry -> combine(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(HistoryPoint::windowStart)).toList();
    }

    private HistoryPoint combine(Instant start, List<HistoryPoint> values) {
        long count = values.stream().mapToLong(HistoryPoint::readingCount).sum();
        double average = values.stream().mapToDouble(value -> value.averageSpeedKmh() * value.readingCount()).sum() / count;
        double min = values.stream().mapToDouble(HistoryPoint::minSpeedKmh).min().orElseThrow();
        double max = values.stream().mapToDouble(HistoryPoint::maxSpeedKmh).max().orElseThrow();
        return new HistoryPoint(values.getFirst().segmentId(), start, average, min, max, count);
    }

    private HistoryPointResponse response(HistoryPoint point) {
        return new HistoryPointResponse(point.segmentId(), point.windowStart(), point.averageSpeedKmh(),
                point.minSpeedKmh(), point.maxSpeedKmh(), point.readingCount());
    }
}
