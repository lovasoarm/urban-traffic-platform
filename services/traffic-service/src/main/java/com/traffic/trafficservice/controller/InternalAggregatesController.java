package com.traffic.trafficservice.controller;

import com.traffic.trafficservice.controller.dto.TrafficAggregateResponse;
import com.traffic.trafficservice.service.TrafficQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/internal/aggregates")
public class InternalAggregatesController {
    private final TrafficQueryService service;

    public InternalAggregatesController(TrafficQueryService service) { this.service = service; }

    @GetMapping
    public List<TrafficAggregateResponse> aggregate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.aggregate(from, to).stream().map(value -> new TrafficAggregateResponse(
                value.segmentId(), value.windowStart(), value.averageSpeedKmh(), value.minSpeedKmh(),
                value.maxSpeedKmh(), value.readingCount())).toList();
    }
}
