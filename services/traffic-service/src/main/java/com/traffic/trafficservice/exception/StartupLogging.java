package com.traffic.trafficservice.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupLogging {
    private static final Logger log = LoggerFactory.getLogger(StartupLogging.class);

    @EventListener(ApplicationReadyEvent.class)
    public void ready() { log.info("traffic-service is ready"); }
}
