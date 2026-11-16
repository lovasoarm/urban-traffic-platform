package com.traffic.historyservice.controller.dto;

import java.util.List;

public record HistoryPageResponse(List<HistoryPointResponse> content, int page, int size,
                                  long totalElements, boolean hasNext) { }
