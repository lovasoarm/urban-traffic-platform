.PHONY: up down logs test smoke clean

up:
	docker compose up --build -d

down:
	docker compose down

logs:
	docker compose logs -f --tail=150

test:
	mvn -B -ntp test

smoke:
	bash scripts/smoke-test.sh

clean:
	docker compose down -v
	find services -type d -name target -prune -exec rm -rf {} +
