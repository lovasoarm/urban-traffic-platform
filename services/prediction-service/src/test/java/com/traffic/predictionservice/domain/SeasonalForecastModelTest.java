package com.traffic.predictionservice.domain;

import org.junit.jupiter.api.Test;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SeasonalForecastModelTest {
    @Test
    void seasonalProfileBeatsLastValueBaselineOnSyntheticWeekPattern() {
        List<ForecastSample> samples = new ArrayList<>();
        Instant start = LocalDate.of(2026, 1, 5).atStartOfDay().toInstant(ZoneOffset.UTC);
        for (int index = 0; index < 14 * 24 * 12; index++) {
            Instant timestamp = start.plusSeconds(index * 300L);
            LocalDateTime local = LocalDateTime.ofInstant(timestamp, ZoneOffset.UTC);
            boolean weekend = local.getDayOfWeek() == DayOfWeek.SATURDAY || local.getDayOfWeek() == DayOfWeek.SUNDAY;
            double peak = !weekend && ((local.getHour() >= 7 && local.getHour() <= 9)
                    || (local.getHour() >= 16 && local.getHour() <= 18)) ? 23 : 0;
            double speed = (weekend ? 51 : 56) - peak + Math.sin(index * 0.17) * 0.1;
            samples.add(new ForecastSample(timestamp, speed));
        }
        ModelFit fit = SeasonalForecastModel.train(samples, 100);
        assertThat(fit.modelMae()).isLessThan(fit.baselineMae());
    }
}
