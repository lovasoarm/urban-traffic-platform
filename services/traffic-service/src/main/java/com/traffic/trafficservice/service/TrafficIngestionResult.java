package com.traffic.trafficservice.service;

/** Summary returned to the API after storage and live-state processing. */
public record TrafficIngestionResult(int inserted, int duplicates, int ignoredForLiveState) { }
