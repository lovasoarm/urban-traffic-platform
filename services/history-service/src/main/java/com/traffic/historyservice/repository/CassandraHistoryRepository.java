package com.traffic.historyservice.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.traffic.historyservice.domain.HistoryPoint;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Repository
public class CassandraHistoryRepository implements HistoryRepository {
    private static final int WRITE_CHUNK_SIZE = 100;
    private final CqlSession session;
    private final PreparedStatement insert;
    private final PreparedStatement select;

    public CassandraHistoryRepository(CqlSession session) {
        this.session = session;
        this.insert = session.prepare("INSERT INTO traffic_stats_by_segment_day " +
                "(segment_id, day, window_start, avg_speed_kmh, min_speed_kmh, max_speed_kmh, reading_count) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)");
        this.select = session.prepare("SELECT segment_id, window_start, avg_speed_kmh, min_speed_kmh, " +
                "max_speed_kmh, reading_count FROM traffic_stats_by_segment_day " +
                "WHERE segment_id = ? AND day = ? AND window_start >= ? AND window_start < ? LIMIT ?");
    }

    @Override
    public void upsertAll(List<HistoryPoint> points) {
        for (int start = 0; start < points.size(); start += WRITE_CHUNK_SIZE) {
            writeChunk(points.subList(start, Math.min(start + WRITE_CHUNK_SIZE, points.size())));
        }
    }

    private void writeChunk(List<HistoryPoint> points) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (HistoryPoint point : points) {
            writes.add(session.executeAsync(insert.bind(point.segmentId(), point.day(), point.windowStart(),
                    point.averageSpeedKmh(), point.minSpeedKmh(), point.maxSpeedKmh(), point.readingCount()))
                    .toCompletableFuture());
        }
        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();
    }

    @Override
    public List<HistoryPoint> findBySegmentAndRange(String segmentId, Instant from, Instant to, int limitPerDay) {
        List<HistoryPoint> points = new ArrayList<>();
        LocalDate day = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate lastDay = to.minusNanos(1).atZone(ZoneOffset.UTC).toLocalDate();
        while (!day.isAfter(lastDay)) {
            points.addAll(readDay(segmentId, day, from, to, limitPerDay));
            day = day.plusDays(1);
        }
        return points.stream().sorted((left, right) -> left.windowStart().compareTo(right.windowStart())).toList();
    }

    private List<HistoryPoint> readDay(String segmentId, LocalDate day, Instant from, Instant to, int limit) {
        Instant dayStart = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant fromBound = from.isAfter(dayStart) ? from : dayStart;
        Instant toBound = to.isBefore(dayStart.plusSeconds(86_400)) ? to : dayStart.plusSeconds(86_400);
        BoundStatement statement = select.bind(segmentId, day,
                fromBound, toBound, limit).setPageSize(500);
        List<HistoryPoint> points = new ArrayList<>();
        for (Row row : session.execute(statement)) points.add(toPoint(row));
        return points;
    }

    private HistoryPoint toPoint(Row row) {
        return new HistoryPoint(row.getString("segment_id"), row.getInstant("window_start"),
                row.getDouble("avg_speed_kmh"), row.getDouble("min_speed_kmh"),
                row.getDouble("max_speed_kmh"), row.getLong("reading_count"));
    }
}
