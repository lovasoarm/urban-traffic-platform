package com.traffic.trafficservice.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traffic.trafficservice.repository.TrafficStateRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises the real MongoDB and Redis adapters together through the HTTP API. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class TrafficPersistenceIntegrationTest {
    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0.48"));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4.12-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("traffic.freshness.max-age", () -> "10m");
        registry.add("traffic.freshness.max-future-skew", () -> "1m");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private TrafficStateRepository trafficStates;

    @Test
    void storesReadingsInMongoAndKeepsRedisLiveStateMonotonic() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String segmentId = "integration-" + suffix;
        String firstReadingId = "first-" + suffix;
        String lateReadingId = "late-" + suffix;
        Instant firstTimestamp = Instant.now().minusSeconds(2);

        mockMvc.perform(post("/api/v1/segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", segmentId, "name", "Integration test road", "speedLimitKmh", 60))))
                .andExpect(status().isCreated());

        JsonNode firstResponse = postReading(firstReadingId, segmentId, 42, firstTimestamp);
        assertThat(firstResponse.get("inserted").asInt()).isEqualTo(1);
        assertThat(firstResponse.get("ignoredForLiveState").asInt()).isZero();

        var firstState = trafficStates.findBySegmentId(segmentId).orElseThrow();
        assertThat(firstState.smoothedSpeedKmh()).isEqualTo(42.0);
        assertThat(firstState.latestReadingAt()).isEqualTo(firstTimestamp);
        assertThat(firstState.measurementCount()).isEqualTo(1);

        JsonNode lateResponse = postReading(lateReadingId, segmentId, 5, firstTimestamp.minusSeconds(1));
        assertThat(lateResponse.get("inserted").asInt()).isEqualTo(1);
        assertThat(lateResponse.get("ignoredForLiveState").asInt()).isEqualTo(1);

        assertThat(mongoTemplate.getCollection("readings").countDocuments(
                new Document("_id", lateReadingId))).isEqualTo(1);
        var finalState = trafficStates.findBySegmentId(segmentId).orElseThrow();
        assertThat(finalState.smoothedSpeedKmh()).isEqualTo(42.0);
        assertThat(finalState.latestReadingAt()).isEqualTo(firstTimestamp);
        assertThat(finalState.measurementCount()).isEqualTo(1);
    }

    private JsonNode postReading(String readingId, String segmentId, double speed, Instant timestamp)
            throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "readingId", readingId,
                "segmentId", segmentId,
                "sensorId", "integration-sensor",
                "type", "FIXED",
                "location", Map.of("latitude", -18.9, "longitude", 47.5),
                "speedKmh", speed,
                "timestamp", timestamp.toString()));
        String body = mockMvc.perform(post("/api/v1/readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }
}
