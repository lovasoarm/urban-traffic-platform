package com.traffic.trafficservice.controller.dto;

import com.traffic.trafficservice.domain.ReadingType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ReadingRequest(
        @NotBlank String readingId,
        @NotBlank String segmentId,
        @NotBlank String sensorId,
        @NotNull ReadingType type,
        String vehicleId,
        @NotNull @Valid LocationRequest location,
        @NotNull @DecimalMin("0.0") @DecimalMax("250.0") Double speedKmh,
        @NotNull Instant timestamp) {
    public record LocationRequest(@NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
                                  @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) { }
}
