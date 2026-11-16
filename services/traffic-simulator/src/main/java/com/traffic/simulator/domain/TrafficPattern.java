package com.traffic.simulator.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;

public final class TrafficPattern {
    private TrafficPattern() { }

    public static double speed(Instant instant, double speedLimit, int segmentIndex, double randomNoise,
                               boolean incident) {
        var local = instant.atZone(ZoneOffset.UTC);
        boolean weekend = local.getDayOfWeek() == DayOfWeek.SATURDAY || local.getDayOfWeek() == DayOfWeek.SUNDAY;
        double ratio = weekend ? 0.82 : 0.88;
        int minuteOfDay = local.getHour() * 60 + local.getMinute();
        if (!weekend && minuteOfDay >= 420 && minuteOfDay <= 570) ratio -= 0.34;
        if (!weekend && minuteOfDay >= 990 && minuteOfDay <= 1170) ratio -= 0.29;
        if (weekend && minuteOfDay >= 600 && minuteOfDay <= 780) ratio -= 0.12;
        ratio -= Math.min(0.08, segmentIndex * 0.004);
        if (incident) ratio = 0.06;
        return Math.max(2.0, Math.min(speedLimit, speedLimit * ratio + randomNoise));
    }
}
