package com.traffic.historyservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(HistoryProperties.class)
public class HistoryConfiguration {
    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean
    RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder().requestFactory(factory).build();
    }
}
