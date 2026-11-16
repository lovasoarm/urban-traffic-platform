package com.traffic.trafficservice.controller;

import com.traffic.trafficservice.controller.dto.TrafficStateResponse;
import com.traffic.trafficservice.domain.CongestionLevel;
import com.traffic.trafficservice.service.TrafficQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/traffic")
public class TrafficController {
    private final TrafficQueryService service;

    public TrafficController(TrafficQueryService service) { this.service = service; }

    @GetMapping("/segments/{id}")
    public TrafficStateResponse getSegment(@PathVariable String id) { return response(service.getSegmentState(id)); }

    @GetMapping("/segments")
    public List<TrafficStateResponse> listSegments(@RequestParam(required = false) String level) {
        return service.listByLevel(level).stream().map(this::response).toList();
    }

    @GetMapping("/congestion/top")
    public List<TrafficStateResponse> top(@RequestParam(defaultValue = "10") int n) {
        return service.topCongestion(n).stream().map(this::response).toList();
    }

    private TrafficStateResponse response(com.traffic.trafficservice.domain.SegmentTrafficState state) {
        return new TrafficStateResponse(state.segmentId(), state.smoothedSpeedKmh(), state.speedLimitKmh(),
                state.level(), state.updatedAt(), state.measurementCount());
    }
}
