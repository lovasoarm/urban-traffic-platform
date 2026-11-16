package com.traffic.trafficservice.controller;

import com.traffic.trafficservice.controller.dto.SegmentRequest;
import com.traffic.trafficservice.controller.dto.SegmentResponse;
import com.traffic.trafficservice.service.SegmentCatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/segments")
public class SegmentsController {
    private final SegmentCatalogService service;

    public SegmentsController(SegmentCatalogService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SegmentResponse create(@Valid @RequestBody SegmentRequest request) {
        var segment = service.save(new com.traffic.trafficservice.domain.RoadSegment(
                request.id(), request.name(), request.speedLimitKmh()));
        return new SegmentResponse(segment.id(), segment.name(), segment.speedLimitKmh());
    }

    @GetMapping
    public List<SegmentResponse> list() {
        return service.findAll().stream().map(segment -> new SegmentResponse(
                segment.id(), segment.name(), segment.speedLimitKmh())).toList();
    }
}
