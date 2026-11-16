package com.traffic.predictionservice.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.DayOfWeek;
import java.util.Map;

public record SeasonalProfile(Map<SlotKey, Double> averageBySlot, double overallAverageSpeedKmh) {
    public record SlotKey(boolean weekend, int quarterHourOfDay) { }

    public double forecast(Instant target, double latestSpeedKmh, int horizonMinutes) {
        var local = target.atZone(ZoneOffset.UTC);
        boolean weekend = local.getDayOfWeek() == DayOfWeek.SATURDAY || local.getDayOfWeek() == DayOfWeek.SUNDAY;
        int quarter = local.getHour() * 4 + local.getMinute() / 15;
        double seasonal = averageBySlot.getOrDefault(new SlotKey(weekend, quarter), overallAverageSpeedKmh);
        double latestCorrection = (latestSpeedKmh - seasonal) * 0.35 * Math.exp(-horizonMinutes / 60.0);
        return Math.max(0.0, Math.min(250.0, seasonal + latestCorrection));
    }
}
