package com.traffic.trafficservice.controller;

import com.traffic.trafficservice.controller.dto.ReadingRequest;
import com.traffic.trafficservice.controller.dto.ReadingsBatchRequest;
import com.traffic.trafficservice.domain.ReadingType;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ReadingValidationTest {
    @Test
    void rejectsInvalidGpsCoordinatesAndSpeed() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var location = new ReadingRequest.LocationRequest(-100.0, 250.0);
        var reading = new ReadingRequest("reading-1", "segment-01", "sensor-1", ReadingType.FIXED,
                null, location, -3.0, Instant.parse("2026-11-14T00:00:00Z"));
        var violations = validator.validate(new ReadingsBatchRequest(List.of(reading)));
        assertThat(violations).hasSizeGreaterThanOrEqualTo(3);
    }
}
