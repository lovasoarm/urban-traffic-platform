package com.traffic.trafficservice.controller.dto;

public record ReadingsBatchResponse(int inserted, int duplicates, int ignoredForLiveState) { }
