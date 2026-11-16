package com.traffic.historyservice.integration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.traffic.historyservice.domain.HistoryPoint;
import com.traffic.historyservice.repository.CassandraHistoryRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the actual Cassandra repository against a disposable Cassandra node. */
@Testcontainers(disabledWithoutDocker = true)
class CassandraHistoryRepositoryIntegrationTest {
    @Container
    static final CassandraContainer<?> CASSANDRA = new CassandraContainer<>(
            DockerImageName.parse("cassandra:4.1.12"));

    private static CqlSession session;
    private static CassandraHistoryRepository repository;

    @BeforeAll
    static void createSchemaAndRepository() {
        session = CqlSession.builder()
                .addContactPoint(CASSANDRA.getContactPoint())
                .withLocalDatacenter(CASSANDRA.getLocalDatacenter())
                .build();
        session.execute("CREATE KEYSPACE traffic WITH replication = " +
                "{'class': 'SimpleStrategy', 'replication_factor': 1}");
        session.execute("CREATE TABLE traffic.traffic_stats_by_segment_day (" +
                "segment_id text, day date, window_start timestamp, avg_speed_kmh double, " +
                "min_speed_kmh double, max_speed_kmh double, reading_count bigint, " +
                "PRIMARY KEY ((segment_id, day), window_start)) WITH CLUSTERING ORDER BY (window_start DESC)");
        session.execute("USE traffic");
        repository = new CassandraHistoryRepository(session);
    }

    @AfterAll
    static void closeSession() {
        if (session != null) session.close();
    }

    @Test
    void writesAndReadsHistoryPointsFromCassandra() {
        String segmentId = "cassandra-it-" + System.nanoTime();
        Instant start = Instant.parse("2026-11-14T08:00:00Z");
        List<HistoryPoint> expected = List.of(
                new HistoryPoint(segmentId, start, 42.5, 38.0, 47.0, 12),
                new HistoryPoint(segmentId, start.plusSeconds(300), 31.0, 25.0, 37.0, 8));

        repository.upsertAll(expected);
        List<HistoryPoint> actual = repository.findBySegmentAndRange(
                segmentId, start, start.plusSeconds(600), 100);

        assertThat(actual).containsExactlyElementsOf(expected);
    }
}
