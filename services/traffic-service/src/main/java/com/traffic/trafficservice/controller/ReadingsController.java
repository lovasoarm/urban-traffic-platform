package com.traffic.trafficservice.controller;

import com.traffic.trafficservice.controller.dto.ReadingsBatchRequest;
import com.traffic.trafficservice.controller.dto.ReadingsBatchResponse;
import com.traffic.trafficservice.service.TrafficIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/readings")
public class ReadingsController {
    private final TrafficIngestionService service;

    public ReadingsController(TrafficIngestionService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReadingsBatchResponse ingest(@Valid @RequestBody ReadingsBatchRequest request) {
        var result = service.ingest(request);
        return new ReadingsBatchResponse(result.inserted(), result.duplicates(), result.ignoredForLiveState());
    }
}
