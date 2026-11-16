package com.traffic.simulator;

import com.traffic.simulator.config.SimulatorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(SimulatorProperties.class)
public class TrafficSimulatorApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(TrafficSimulatorApplication.class);
        application.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        application.run(args);
    }

    @Bean
    Clock clock() { return Clock.systemUTC(); }
}
