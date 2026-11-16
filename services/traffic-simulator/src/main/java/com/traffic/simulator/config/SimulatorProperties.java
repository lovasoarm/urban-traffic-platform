package com.traffic.simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "simulator")
public record SimulatorProperties(String gatewayUrl, int seedDays, long intervalMs, int segmentCount,
                                  int measurementsPerSegment, double centerLatitude, double centerLongitude,
                                  long randomSeed) { }
