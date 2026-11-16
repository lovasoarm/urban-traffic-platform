# Architecture et contrats figés

Ce document fixe les noms, ports, routes, variables, schémas et choix de données. Les fichiers Compose, les configurations Spring, Prometheus et Grafana doivent conserver ces contrats.

## 1. Services et ports

| Service | Application | Port REST interne | Port management interne | Données / rôle |
|---|---|---:|---:|---|
| `gateway` | Spring Cloud Gateway | 8080 | 9080 | Routage REST, CORS, aucune logique métier |
| `traffic-service` | Spring Boot MVC | 8081 | 9081 | MongoDB + Redis |
| `history-service` | Spring Boot MVC | 8082 | 9082 | Cassandra et collecte planifiée |
| `prediction-service` | Spring Boot MVC | 8083 | 9083 | Modèles saisonniers en mémoire, historique via REST |
| `traffic-simulator` | Spring Boot non-web | aucun | 9084 | Seed et mesures générées automatiquement |

Seuls les ports hôte 8080, 9090 et 3000 sont publiés. Les ports management restent sur le réseau Docker. Le simulateur reste sans Spring MVC/WebFlux ; il expose les deux chemins `/actuator/health` et `/actuator/prometheus` au moyen du petit serveur HTTP du JDK afin de rester scrapeable sans introduire un serveur Spring Web.

Dépendances au démarrage :

- `traffic-service` attend MongoDB et Redis sains.
- `cassandra-init` attend Cassandra saine et exécute `infra/cassandra/schema.cql`.
- `history-service` attend Cassandra, l'init CQL terminée et `traffic-service` sain.
- `prediction-service` attend `history-service` sain.
- `gateway` attend les trois services REST sains.
- `traffic-simulator` attend la gateway et l'historique sains, puis exécute le seed avant de devenir sain.
- Prometheus attend les cinq cibles ; Grafana attend Prometheus.

## 2. Routes REST

### Gateway

| Préfixe | Destination |
|---|---|
| `/api/v1/readings`, `/api/v1/readings/**` | `traffic-service:8081` |
| `/api/v1/traffic/**` | `traffic-service:8081` |
| `/api/v1/segments`, `/api/v1/segments/**` | `traffic-service:8081` |
| `/api/v1/vehicles/**` | `traffic-service:8081` |
| `/api/v1/history/**` | `history-service:8082` |
| `/api/v1/predictions/**` | `prediction-service:8083` |

Aucune route gateway ne pointe vers `/actuator/**` ou `/internal/**`. Les appels de service à service utilisent directement les noms DNS Docker et les ports internes. CORS accepte par défaut `http://localhost:3000`, réglable par `GATEWAY_ALLOWED_ORIGINS`.

### Traffic service

| Méthode | Endpoint | Contrat |
|---|---|---|
| POST | `/api/v1/readings` | Batch de 1 à 500 mesures ; doublons `readingId` ignorés |
| GET | `/api/v1/traffic/segments/{id}` | État temps réel Redis |
| GET | `/api/v1/traffic/segments?level=` | États filtrés par `FREE`, `DENSE`, `CONGESTED`, `BLOCKED` |
| GET | `/api/v1/traffic/congestion/top?n=` | Classement Redis ZSET, limite 1 à 50 |
| GET | `/api/v1/vehicles/{id}/trajectory?from=&to=` | GeoJSON `LineString`, coordonnées `[longitude, latitude]` |
| POST | `/api/v1/segments` | Création ou mise à jour idempotente d'un tronçon |
| GET | `/api/v1/segments` | Catalogue |
| GET | `/internal/aggregates?from=&to=` | Agrégats Mongo par tronçon et fenêtre UTC de 5 minutes |

Les ratios de vitesse sont les seuils initiaux : `FREE >= 0.70`, `DENSE >= 0.40`, `CONGESTED >= 0.15`, sinon `BLOCKED`. Le lissage utilise `0.35 * vitesseBatch + 0.65 * vitessePrécédente` ; ces valeurs sont configurables dans `traffic-service`.

### History service

| Méthode | Endpoint | Contrat |
|---|---|---|
| GET | `/api/v1/history/segments/{id}?from=&to=&granularity=5m\|1h\|1d&page=&size=` | Historique paginé ; intervalle maximum 31 jours |
| POST | `/api/v1/history/import` | Upsert idempotent en lots de 1 à 5000 statistiques |

Toutes les cinq minutes, le collecteur prend les trois fenêtres closes précédentes dans `traffic-service` et les upsert dans Cassandra. Il utilise une seule relance REST en cas d'erreur transitoire.

### Prediction service

| Méthode | Endpoint | Contrat |
|---|---|---|
| GET | `/api/v1/predictions/segments/{id}?horizonMinutes=` | Prévision de vitesse, horizon 1 à 120 minutes |
| GET | `/api/v1/predictions/hotspots?horizonMinutes=30` | Tronçons triés du plus lent au plus rapide |
| GET | `/api/v1/predictions/model` | Disponibilité, date d'entraînement, MAE et baseline par tronçon |

Une absence de modèle renvoie `503` avec `ProblemDetail`. Le démarrage tente un entraînement, les modèles absents sont réessayés toutes les 30 secondes et les modèles disponibles sont réentraînés toutes les heures.

## 3. Variables d'environnement

Les variables opérationnelles sont listées dans `.env.example`. Valeurs importantes :

| Variable | Défaut | Usage |
|---|---:|---|
| `MONGO_IMAGE` | `mongo:7.0.48` | Image MongoDB 7 épinglée |
| `REDIS_IMAGE` | `redis:7.4.12-alpine` | Image Redis 7 épinglée |
| `CASSANDRA_IMAGE` | `cassandra:4.1.12` | Image Cassandra 4.1 épinglée |
| `PROMETHEUS_IMAGE` | `prom/prometheus:v3.16.0` | Image Prometheus épinglée |
| `GRAFANA_IMAGE` | `grafana/grafana-oss:13.3.1` | Image Grafana OSS épinglée |
| `MONGO_DATABASE` | `urban_traffic` | Base MongoDB |
| `MONGO_RETENTION_DAYS` | `7` | TTL MongoDB en jours |
| `REDIS_TTL_SECONDS` | `600` | TTL des états et du ranking |
| `CASSANDRA_DATACENTER` | `datacenter1` | Datacenter local Cassandra (keyspace `traffic` fixé dans le schéma) |
| `TRAFFIC_MAX_BATCH_SIZE` | `500` | Taille maximale de batch |
| `TRAFFIC_SMOOTHING_FACTOR` | `0.35` | Poids de la nouvelle vitesse |
| `CONGESTION_FREE_RATIO` | `0.70` | Seuil `FREE` |
| `CONGESTION_DENSE_RATIO` | `0.40` | Seuil `DENSE` |
| `CONGESTION_CONGESTED_RATIO` | `0.15` | Seuil `CONGESTED` |
| `HISTORY_MAX_IMPORT_BATCH_SIZE` | `5000` | Taille maximale d'import |
| `HISTORY_MAX_RANGE_DAYS` | `31` | Fenêtre historique API |
| `HISTORY_COLLECTOR_FIXED_RATE_MS` | `300000` | Cadence d'archivage |
| `PREDICTION_SEGMENTS` | 10 IDs `segment-01` à `segment-10` | Segments entraînés |
| `PREDICTION_LOOKBACK_DAYS` | `14` | Fenêtre d'apprentissage |
| `PREDICTION_MINIMUM_SAMPLES` | `100` | Seuil minimal de données |
| `PREDICTION_TRAINING_FIXED_RATE_MS` | `3600000` | Réentraînement horaire |
| `PREDICTION_RETRY_FIXED_DELAY_MS` | `30000` | Relance avant première disponibilité |
| `SIMULATOR_SEED_DAYS` | `14` | Profondeur du seed initial |
| `SIMULATOR_INTERVAL_MS` | `5000` | Cadence des mesures live |
| `SIMULATOR_SEGMENT_COUNT` | `10` | Nombre de tronçons générés |
| `SIMULATOR_MEASUREMENTS_PER_SEGMENT` | `3` | Mesures envoyées par tronçon et par batch |
| `SIMULATOR_CENTER_LAT`, `SIMULATOR_CENTER_LON` | `-18.8792`, `47.5079` | Centre du réseau GPS synthétique |
| `SIMULATOR_RANDOM_SEED` | `20261114` | Reproductibilité du seed |

Les variables des ports hôte et du compte Grafana sont également explicitées dans `.env.example`.

## 4. Modèles de données

### MongoDB

Base `urban_traffic`, collection `readings` :

| Champ | Type / sens |
|---|---|
| `_id` | `readingId`, clé d'idempotence ; les doublons sont ignorés |
| `segmentId`, `sensorId` | Identifiants du tronçon et du capteur |
| `type` | `FIXED` ou `GPS_PROBE` |
| `vehicleId` | Identifiant facultatif du véhicule |
| `location` | GeoJSON `Point`, coordonnées `[longitude, latitude]` |
| `speedKmh` | Vitesse observée en km/h |
| `timestamp` | Instant UTC ; TTL de 7 jours configurable |

Index : composé `(segmentId, timestamp)`, composé `(vehicleId, timestamp)`, géospatial `2dsphere`, TTL `timestamp`. Les index métier sont déclarés sur le modèle Spring Data ; les index sont explicitement assurés au démarrage. Le TTL est créé par configuration au démarrage parce que la valeur d'une annotation Java ne peut pas être liée à une variable d'environnement.

Collection `road_segments` : `_id`, `name`, `speedLimitKmh`.

### Redis

- `segment:{id}` : HASH contenant `segmentId`, vitesse lissée, vitesse limite, niveau, date UTC de mise à jour et compteur de mesures ; TTL par défaut 600 secondes.
- `congestion:ranking` : ZSET trié par sévérité puis vitesse relative ; TTL renouvelé à chaque mise à jour. Lors d'une lecture du classement, les membres dont le HASH `segment:{id}` a expiré sont retirés.
- Une seule invocation Lua par tronçon touché et par batch effectue l'écriture atomique du HASH et du ZSET ainsi que leurs expirations. Aucune commande `KEYS` n'est utilisée.

### Cassandra

Keyspace `traffic`, table `traffic_stats_by_segment_day` :

```sql
PRIMARY KEY ((segment_id, day), window_start)
WITH CLUSTERING ORDER BY (window_start DESC)
```

Colonnes : `avg_speed_kmh`, `min_speed_kmh`, `max_speed_kmh`, `reading_count`. Les lectures bornent une partition par segment et jour puis filtrent sur la clé de clustering ; aucune requête `ALLOW FILTERING`. Les écritures sont des upserts préparés, rejouables sans doublons.

## 5. Pourquoi ces bases

- **MongoDB** garde les mesures brutes semi-structurées : un même format accueille capteurs fixes et sondes GPS, les index géospatiaux accélèrent les trajectoires et les index temporels servent les agrégations. La TTL limite le volume opérationnel à sept jours.
- **Redis** porte uniquement l'état chaud : lecture de l'état d'un tronçon et classement de congestion doivent être immédiats. Hashes et ZSET sont adaptés à ces accès ; la TTL évite qu'un état périmé reste considéré comme courant.
- **Cassandra** conserve les statistiques historiques append/upsert par jour et tronçon. La clé de partition borne le volume lu ; le clustering temporel facilite les plages récentes et les agrégats préparés.

Ces bases ne sont pas interchangeables : Mongo conserve l'événement brut, Redis l'état calculé courant, Cassandra l'agrégat historisé.

## 6. Modèle de prévision et évaluation

Pour chaque tronçon, les mesures historiques sont triées par temps et séparées chronologiquement : 80 % pour apprendre le profil, 20 % pour l'évaluation. Le profil contient la vitesse moyenne par quart d'heure et par classe de jour (`weekday` ou `weekend`). La prévision combine le profil du créneau futur et un correctif lié à la dernière vitesse connue ; ce correctif décroît exponentiellement avec l'horizon. La MAE est calculée sur les 20 % les plus récents et comparée à la baseline qui reprend la dernière vitesse observée.

Le modèle est volontairement explicable et léger. Il ne prétend pas modéliser les réseaux routiers, les dépendances entre tronçons, les événements externes ou les changements de capacité. Il renvoie une vitesse prévue, pas une ETA ni un itinéraire optimisé.

## 7. Monitoring

Prometheus scrape toutes les 5 secondes :

| Cible | Endpoint |
|---|---|
| gateway | `gateway:9080/actuator/prometheus` |
| traffic-service | `traffic-service:9081/actuator/prometheus` |
| history-service | `history-service:9082/actuator/prometheus` |
| prediction-service | `prediction-service:9083/actuator/prometheus` |
| traffic-simulator | `traffic-simulator:9084/actuator/prometheus` |

Métriques métier traffic-service : `traffic_readings_ingested_total`, `traffic_segments_congested`, `traffic_avg_speed_kmh{segment}`. Prévision : `traffic_forecast_mae`. Le simulateur non-web expose des métriques de seed et batches via HTTP JDK compatible format Prometheus ; les quatre services REST utilisent Actuator/Micrometer. Les gauges métier retiennent leurs `AtomicReference` dans une map pour éviter la collecte des références faibles.

Le dashboard provisionné `Trafic` affiche congestion, vitesse par tronçon, débit de mesures, MAE, mémoire JVM et débit HTTP.

## 8. Versions, exploitation et limites connues

Les limites `mem_limit` du Compose totalisent 5 184 MiB (environ 5,06 Gio), hors mémoire réservée à l’hôte et aux caches du moteur Docker. Cassandra est plafonnée à 1 024 MiB avec un heap Java de 512 MiB.

- Java 21 (runtime JRE Alpine Temurin 21.0.13_9) ; Spring Boot 3.5.16 ; Spring Cloud 2025.0.3 pour la gateway uniquement ; Maven 3.9.16 (image de build Temurin 21 Alpine).
- MongoDB 7.0.48 ; Redis 7.4.12 ; Cassandra 4.1.12 ; Prometheus 3.16.0 ; Grafana OSS 13.3.1.
- Boot 3.5.16 est le dernier correctif OSS de la branche 3.5.x ; la branche n'est plus sous support OSS. Spring Cloud 2025.0.3 est la version compatible Boot 3.5 documentée dans cette ligne, mais son support OSS a pris fin. Ces versions sont conservées pour respecter le cadre demandé ; planifier une migration vers une génération Spring maintenue pour une exploitation réelle.
- Cassandra est un nœud unique en `SimpleStrategy` RF=1, adapté à la démonstration, pas à la haute disponibilité.
- Les bases locales n'ont pas d'authentification ; aucun TLS, RBAC, rate limit ou gestionnaire de secrets n'est activé.
- Les données simulées utilisent des coordonnées centrées par défaut sur Antananarivo ; elles ne représentent pas un réseau cartographié réel.
- Le seed 14 jours est un profil synthétique et déterministe, pas une donnée terrain. Les prévisions reflètent donc le simulateur.
- Le cache Redis expire après 10 minutes ; un segment sans nouvelles mesures perd son état temps réel.
- Les événements bruts Mongo sont supprimés après 7 jours ; l'historique agrégé Cassandra reste conservé.
- Les appels REST inter-services ont un timeout et une seule relance ; il n'y a ni broker ni traitement distribué hors REST.

Évolutions possibles hors de ce périmètre : Kafka pour découpler l'ingestion, Kubernetes pour l'orchestration, authentification, chiffrement, réplication Cassandra, vraie cartographie et modèles de prévision multivariés.
