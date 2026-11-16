package com.traffic.trafficservice.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TrafficAggregationTest {
    @Test
    void calculatesAverageMinimumMaximumAndCount() {
        var statistics = TrafficAggregation.summarizeSpeeds(List.of(10.0, 20.0, 30.0));
        assertThat(statistics.average()).isEqualTo(20.0);
        assertThat(statistics.minimum()).isEqualTo(10.0);
        assertThat(statistics.maximum()).isEqualTo(30.0);
        assertThat(statistics.count()).isEqualTo(3);
    }
}
