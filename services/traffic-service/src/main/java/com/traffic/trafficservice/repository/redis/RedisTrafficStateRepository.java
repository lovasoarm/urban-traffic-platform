package com.traffic.trafficservice.repository.redis;

import com.traffic.trafficservice.domain.CongestionLevel;
import com.traffic.trafficservice.domain.SegmentTrafficState;
import com.traffic.trafficservice.repository.TrafficStateRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class RedisTrafficStateRepository implements TrafficStateRepository {
    private static final String SAVE_SCRIPT = "redis.call('HSET', KEYS[1], 'segmentId', ARGV[1], 'speed', ARGV[2], 'speedLimit', ARGV[3], 'level', ARGV[4], 'updatedAt', ARGV[5], 'latestReadingAt', ARGV[6], 'count', ARGV[7]); " +
            "redis.call('EXPIRE', KEYS[1], ARGV[8]); redis.call('ZADD', KEYS[2], ARGV[9], ARGV[1]); " +
            "redis.call('EXPIRE', KEYS[2], ARGV[8]); return 1";
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> saveScript;

    public RedisTrafficStateRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.saveScript = new DefaultRedisScript<>(SAVE_SCRIPT, Long.class);
    }

    @Override
    public void save(SegmentTrafficState state, long ttlSeconds) {
        double severity = switch (state.level()) {
            case FREE -> 0.0;
            case DENSE -> 1.0;
            case CONGESTED -> 2.0;
            case BLOCKED -> 3.0;
        };
        redisTemplate.execute(saveScript,
                List.of("segment:" + state.segmentId(), "congestion:ranking"),
                state.segmentId(), Double.toString(state.smoothedSpeedKmh()), Double.toString(state.speedLimitKmh()),
                state.level().name(), state.updatedAt().toString(), state.latestReadingAt().toString(),
                Long.toString(state.measurementCount()), Long.toString(ttlSeconds),
                Double.toString(severity * 1000 + (1 - state.smoothedSpeedKmh() / state.speedLimitKmh())));
    }

    @Override
    public Optional<SegmentTrafficState> findBySegmentId(String segmentId) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries("segment:" + segmentId);
        if (values.isEmpty()) return Optional.empty();
        return Optional.of(new SegmentTrafficState(segmentId, Double.parseDouble((String) values.get("speed")),
                Double.parseDouble((String) values.get("speedLimit")), CongestionLevel.valueOf((String) values.get("level")),
                Instant.parse((String) values.get("updatedAt")),
                values.containsKey("latestReadingAt") ? Instant.parse((String) values.get("latestReadingAt"))
                        : Instant.parse((String) values.get("updatedAt")),
                Long.parseLong((String) values.get("count"))));
    }

    @Override
    public List<String> findCongestionRanking(int limit) {
        var ranking = redisTemplate.opsForZSet().reverseRange("congestion:ranking", 0, -1);
        if (ranking == null || ranking.isEmpty()) return List.of();
        List<String> active = ranking.stream().map(String.class::cast)
                .filter(segmentId -> Boolean.TRUE.equals(redisTemplate.hasKey("segment:" + segmentId))).toList();
        List<String> expired = ranking.stream().map(String.class::cast)
                .filter(segmentId -> !active.contains(segmentId)).toList();
        if (!expired.isEmpty()) redisTemplate.opsForZSet().remove("congestion:ranking", expired.toArray());
        return active.stream().limit(limit).toList();
    }
}
