package com.traffic.trafficservice;

import com.traffic.trafficservice.config.TrafficProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TrafficProperties.class)
public class TrafficServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrafficServiceApplication.class, args);
    }
}
