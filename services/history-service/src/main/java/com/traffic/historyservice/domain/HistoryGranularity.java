package com.traffic.historyservice.domain;

public enum HistoryGranularity {
    FIVE_MINUTES("5m"), ONE_HOUR("1h"), ONE_DAY("1d");

    private final String apiValue;
    HistoryGranularity(String apiValue) { this.apiValue = apiValue; }
    public String apiValue() { return apiValue; }

    public static HistoryGranularity parse(String value) {
        return switch (value.toLowerCase()) {
            case "5m" -> FIVE_MINUTES;
            case "1h" -> ONE_HOUR;
            case "1d" -> ONE_DAY;
            default -> throw new IllegalArgumentException("granularity must be 5m, 1h or 1d");
        };
    }
}
