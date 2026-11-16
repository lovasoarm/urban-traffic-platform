#!/usr/bin/env bash
set -euo pipefail

if [[ -f .env ]]; then
  set -a
  # .env contains simple KEY=value entries from .env.example.
  source ./.env
  set +a
fi

BASE_URL="${BASE_URL:-http://localhost:8080}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3000}"

wait_for_url() {
  local url="$1" description="$2" attempts="${3:-30}"
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if curl --silent --fail "$url" >/dev/null; then
      printf 'OK  %s\n' "$description"
      return 0
    fi
    sleep 2
  done
  printf 'FAIL %s (%s)\n' "$description" "$url" >&2
  return 1
}

check_internal_health() {
  local service="$1" port="$2"
  docker compose exec -T "$service" sh -c "wget -q -O - http://127.0.0.1:${port}/actuator/health" >/dev/null
  printf 'OK  %s health\n' "$service"
}

check_internal_health gateway 9080
check_internal_health traffic-service 9081
check_internal_health history-service 9082
check_internal_health prediction-service 9083
check_internal_health traffic-simulator 9084
wait_for_url "$PROMETHEUS_URL/-/healthy" "Prometheus" 30
wait_for_url "$GRAFANA_URL/api/health" "Grafana" 30

NOW="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
FROM="$(date -u -d '14 days ago' +%Y-%m-%dT%H:%M:%SZ)"

curl --silent --show-error --fail \
  -H 'Content-Type: application/json' \
  -X POST "$BASE_URL/api/v1/segments" \
  -d '{"id":"smoke-segment","name":"Smoke test road","speedLimitKmh":50}' >/dev/null

curl --silent --show-error --fail \
  -H 'Content-Type: application/json' \
  -X POST "$BASE_URL/api/v1/readings" \
  -d "{\"readings\":[{\"readingId\":\"smoke-$(date +%s)\",\"segmentId\":\"smoke-segment\",\"sensorId\":\"smoke-sensor\",\"type\":\"FIXED\",\"location\":{\"latitude\":-18.8792,\"longitude\":47.5079},\"speedKmh\":8.5,\"timestamp\":\"$NOW\"}]}" >/dev/null

wait_for_url "$BASE_URL/api/v1/traffic/segments/smoke-segment" "traffic state" 10
wait_for_url "$BASE_URL/api/v1/history/segments/segment-01?from=$FROM&to=$NOW&granularity=5m&size=5000" "history API" 10

for attempt in $(seq 1 40); do
  if curl --silent --fail "$BASE_URL/api/v1/predictions/segments/segment-01?horizonMinutes=15" >/dev/null; then
    printf 'OK  forecast API\n'
    break
  fi
  if [[ "$attempt" -eq 40 ]]; then
    printf 'FAIL forecast API did not become ready\n' >&2
    exit 1
  fi
  sleep 3
done

for target in gateway traffic-service history-service prediction-service traffic-simulator; do
  target_ready=false
  for attempt in $(seq 1 30); do
    response="$(curl --silent --show-error --fail -G \
      --data-urlencode "query=up{job=\"$target\"}" "$PROMETHEUS_URL/api/v1/query")"
    if [[ "$response" == *'"1"'* ]]; then
      target_ready=true
      break
    fi
    sleep 2
  done
  if [[ "$target_ready" != true ]]; then
    printf 'FAIL Prometheus target is not up: %s\n' "$target" >&2
    exit 1
  fi
  printf 'OK  Prometheus target %s\n' "$target"
done

grafana_ready=false
for attempt in $(seq 1 15); do
  if curl --silent --fail -u "${GRAFANA_ADMIN_USER:-admin}:${GRAFANA_ADMIN_PASSWORD:-admin}" \
      "$GRAFANA_URL/api/dashboards/uid/urban-traffic-live" | grep -q 'Trafic'; then
    grafana_ready=true
    break
  fi
  sleep 2
done
if [[ "$grafana_ready" != true ]]; then
  printf 'FAIL Grafana dashboard not provisioned\n' >&2
  exit 1
fi
printf 'OK  Grafana dashboard provisioned\n'
printf '\nSmoke test passed.\n'
