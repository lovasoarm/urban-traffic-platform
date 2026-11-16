package com.traffic.trafficservice.controller;

import com.traffic.trafficservice.controller.dto.GeoJsonLineStringResponse;
import com.traffic.trafficservice.service.TrafficQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehiclesController {
    private final TrafficQueryService service;

    public VehiclesController(TrafficQueryService service) { this.service = service; }

    @GetMapping("/{id}/trajectory")
    public GeoJsonLineStringResponse trajectory(@PathVariable String id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return new GeoJsonLineStringResponse("LineString", service.trajectory(id, from, to));
    }
}
