package com.traffic.simulator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import java.util.function.Supplier;

@Component
public class RestCallExecutor {
    private static final Logger log = LoggerFactory.getLogger(RestCallExecutor.class);

    public <T> T execute(String operation, Supplier<T> request) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return request.get();
            } catch (HttpClientErrorException exception) {
                throw exception;
            } catch (RestClientException exception) {
                if (attempt == 2) throw exception;
                log.warn("Transient REST failure for {}; retrying once", operation, exception);
            }
        }
        throw new IllegalStateException("Unreachable retry state for " + operation);
    }
}
