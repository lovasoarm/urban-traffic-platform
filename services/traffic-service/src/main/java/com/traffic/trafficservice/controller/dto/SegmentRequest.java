package com.traffic.trafficservice.controller.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

public record SegmentRequest(@NotBlank String id, @NotBlank String name,
                             @DecimalMin("1.0") double speedLimitKmh) { }
