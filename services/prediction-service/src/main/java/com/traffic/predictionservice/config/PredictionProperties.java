package com.traffic.predictionservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "prediction")
public record PredictionProperties(String historyServiceUrl, List<String> segments,
                                  int lookbackDays, int minimumSamples, long trainingFixedRateMs,
                                  int historyPageSize, int retryCount) { }
