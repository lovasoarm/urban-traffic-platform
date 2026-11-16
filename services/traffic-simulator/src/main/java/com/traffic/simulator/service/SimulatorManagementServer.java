package com.traffic.simulator.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.lang.management.ManagementFactory;

@Component
public class SimulatorManagementServer {
    private final int port;
    private final SimulatorMetrics metrics;
    private HttpServer server;

    public SimulatorManagementServer(@Value("${simulator.management-port:9084}") int port,
                                     SimulatorMetrics metrics) {
        this.port = port;
        this.metrics = metrics;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/actuator/health", exchange -> respond(exchange, 200,
                "application/json", "{\"status\":\"UP\"}"));
        server.createContext("/actuator/prometheus", this::writeMetrics);
        server.start();
    }

    private void writeMetrics(HttpExchange exchange) throws IOException {
        long heapUsed = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        String body = "# HELP traffic_simulator_live_batches_total Live batches sent\n" +
                "# TYPE traffic_simulator_live_batches_total counter\n" +
                "traffic_simulator_live_batches_total " + metrics.liveBatches() + "\n" +
                "# HELP traffic_simulator_seeded_rows_total Historical rows submitted\n" +
                "# TYPE traffic_simulator_seeded_rows_total counter\n" +
                "traffic_simulator_seeded_rows_total " + metrics.seededRows() + "\n" +
                "# HELP jvm_memory_used_bytes Used JVM heap memory\n" +
                "# TYPE jvm_memory_used_bytes gauge\n" +
                "jvm_memory_used_bytes{application=\"traffic-simulator\",area=\"heap\"} " + heapUsed + "\n";
        respond(exchange, 200, "text/plain; version=0.0.4; charset=utf-8", body);
    }

    private void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] content = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, content.length);
        try (var output = exchange.getResponseBody()) { output.write(content); }
    }

    @PreDestroy
    public void stop() {
        if (server != null) server.stop(0);
    }
}
