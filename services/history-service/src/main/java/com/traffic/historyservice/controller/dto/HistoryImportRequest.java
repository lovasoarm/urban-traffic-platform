package com.traffic.historyservice.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record HistoryImportRequest(@NotEmpty @Size(max = 5000) List<@Valid HistoryPointRequest> points) { }
