package com.traffic.historyservice.controller;

import com.traffic.historyservice.controller.dto.HistoryImportRequest;
import com.traffic.historyservice.controller.dto.HistoryImportResponse;
import com.traffic.historyservice.controller.dto.HistoryPageResponse;
import com.traffic.historyservice.domain.HistoryGranularity;
import com.traffic.historyservice.domain.HistoryPoint;
import com.traffic.historyservice.service.HistoryService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/history")
public class HistoryController {
    private final HistoryService service;

    public HistoryController(HistoryService service) { this.service = service; }

    @GetMapping("/segments/{id}")
    public HistoryPageResponse getSegment(@PathVariable String id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "5m") String granularity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "500") int size) {
        return service.getHistory(id, from, to, HistoryGranularity.parse(granularity), page, size);
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public HistoryImportResponse importHistory(@Valid @RequestBody HistoryImportRequest request) {
        int count = service.importPoints(request.points().stream().map(point -> new HistoryPoint(
                point.segmentId(), point.windowStart(), point.averageSpeedKmh(), point.minSpeedKmh(),
                point.maxSpeedKmh(), point.readingCount())).toList());
        return new HistoryImportResponse(count);
    }
}
