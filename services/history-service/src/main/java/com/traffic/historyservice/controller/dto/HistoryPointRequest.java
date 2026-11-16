package com.traffic.historyservice.controller.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

public record HistoryPointRequest(@NotBlank String segmentId, @NotNull Instant windowStart,
                                  @NotNull @DecimalMin("0.0") @DecimalMax("250.0") Double averageSpeedKmh,
                                  @NotNull @DecimalMin("0.0") @DecimalMax("250.0") Double minSpeedKmh,
                                  @NotNull @DecimalMin("0.0") @DecimalMax("250.0") Double maxSpeedKmh,
                                  @NotNull @Positive Long readingCount) { }
