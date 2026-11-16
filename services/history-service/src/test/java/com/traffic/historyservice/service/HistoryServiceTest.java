package com.traffic.historyservice.service;

import com.traffic.historyservice.config.HistoryProperties;
import com.traffic.historyservice.domain.HistoryGranularity;
import com.traffic.historyservice.domain.HistoryPoint;
import com.traffic.historyservice.repository.HistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {
    @Mock private HistoryRepository repository;

    @Test
    void hourlyAggregationUsesReadingCountWeightedAverage() {
        var service = new HistoryService(repository, new HistoryProperties(
                "http://traffic-service:8081", 5000, 31, new HistoryProperties.Collector(300000, 20000, 1)));
        Instant from = Instant.parse("2026-11-14T08:00:00Z");
        Instant to = Instant.parse("2026-11-14T09:00:00Z");
        when(repository.findBySegmentAndRange("segment-01", from, to, 5000)).thenReturn(List.of(
                new HistoryPoint("segment-01", from, 60, 55, 65, 1),
                new HistoryPoint("segment-01", from.plusSeconds(300), 30, 20, 40, 3),
                new HistoryPoint("segment-01", from.plusSeconds(600), 40, 35, 45, 6)));

        var page = service.getHistory("segment-01", from, to, HistoryGranularity.ONE_HOUR, 0, 50);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().averageSpeedKmh()).isEqualTo(39.0);
        assertThat(page.content().getFirst().minSpeedKmh()).isEqualTo(20.0);
        assertThat(page.content().getFirst().maxSpeedKmh()).isEqualTo(65.0);
        assertThat(page.content().getFirst().readingCount()).isEqualTo(10);
    }
}
