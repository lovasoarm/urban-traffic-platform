package com.traffic.trafficservice.controller.dto;

import java.util.List;

public record GeoJsonLineStringResponse(String type, List<List<Double>> coordinates) { }
