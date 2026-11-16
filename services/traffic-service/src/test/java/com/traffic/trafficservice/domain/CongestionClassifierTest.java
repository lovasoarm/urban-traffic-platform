package com.traffic.trafficservice.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CongestionClassifierTest {
    private final CongestionThresholds thresholds = new CongestionThresholds(0.70, 0.40, 0.15);

    @Test
    void classifiesSpeedAsFree() {
        assertThat(CongestionClassifier.classify(42, 60, thresholds)).isEqualTo(CongestionLevel.FREE);
    }

    @Test
    void classifiesDenseCongestedAndBlocked() {
        assertThat(CongestionClassifier.classify(30, 60, thresholds)).isEqualTo(CongestionLevel.DENSE);
        assertThat(CongestionClassifier.classify(12, 60, thresholds)).isEqualTo(CongestionLevel.CONGESTED);
        assertThat(CongestionClassifier.classify(4, 60, thresholds)).isEqualTo(CongestionLevel.BLOCKED);
    }
}
