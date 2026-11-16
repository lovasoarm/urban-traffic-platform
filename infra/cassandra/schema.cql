CREATE KEYSPACE IF NOT EXISTS traffic
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE IF NOT EXISTS traffic.traffic_stats_by_segment_day (
    segment_id text,
    day date,
    window_start timestamp,
    avg_speed_kmh double,
    min_speed_kmh double,
    max_speed_kmh double,
    reading_count bigint,
    PRIMARY KEY ((segment_id, day), window_start)
) WITH CLUSTERING ORDER BY (window_start DESC)
  AND comment = '5-minute traffic aggregates partitioned by segment and UTC day';
