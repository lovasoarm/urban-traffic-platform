package com.traffic.predictionservice.domain;

import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class SeasonalForecastModel {
    private SeasonalForecastModel() { }

    public static ModelFit train(List<ForecastSample> source, int minimumSamples) {
        List<ForecastSample> samples = source.stream().sorted(Comparator.comparing(ForecastSample::timestamp)).toList();
        if (samples.size() < minimumSamples) {
            throw new InsufficientHistoryException("Need at least " + minimumSamples + " historical samples; received " + samples.size());
        }
        int split = Math.max(1, (int) Math.floor(samples.size() * 0.80));
        List<ForecastSample> training = samples.subList(0, split);
        List<ForecastSample> evaluation = samples.subList(split, samples.size());
        SeasonalProfile profile = profile(training);
        double[] errors = evaluate(profile, training.getLast(), evaluation);
        double modelMae = errors[0] / evaluation.size();
        double baselineMae = errors[1] / evaluation.size();
        return new ModelFit(profile, modelMae, baselineMae, samples.getLast().speedKmh(), training.size(), evaluation.size());
    }

    private static SeasonalProfile profile(List<ForecastSample> samples) {
        Map<SeasonalProfile.SlotKey, Double> means = samples.stream().collect(Collectors.groupingBy(
                SeasonalForecastModel::slot, Collectors.averagingDouble(ForecastSample::speedKmh)));
        double overall = samples.stream().mapToDouble(ForecastSample::speedKmh).average().orElseThrow();
        return new SeasonalProfile(Map.copyOf(means), overall);
    }

    private static double[] evaluate(SeasonalProfile profile, ForecastSample initial,
                                     List<ForecastSample> evaluation) {
        double modelError = 0;
        double baselineError = 0;
        double latest = initial.speedKmh();
        for (ForecastSample actual : evaluation) {
            double baseline = latest;
            double forecast = profile.forecast(actual.timestamp(), latest, 5);
            modelError += Math.abs(actual.speedKmh() - forecast);
            baselineError += Math.abs(actual.speedKmh() - baseline);
            latest = actual.speedKmh();
        }
        return new double[]{modelError, baselineError};
    }

    private static SeasonalProfile.SlotKey slot(ForecastSample sample) {
        var local = sample.timestamp().atZone(ZoneOffset.UTC);
        boolean weekend = local.getDayOfWeek() == DayOfWeek.SATURDAY || local.getDayOfWeek() == DayOfWeek.SUNDAY;
        return new SeasonalProfile.SlotKey(weekend, local.getHour() * 4 + local.getMinute() / 15);
    }
}
