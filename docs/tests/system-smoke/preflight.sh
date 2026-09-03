#!/usr/bin/env bash
# Run on the VPS. Read-only; does not restart services, change offsets, or purge data.
set -euo pipefail
command -v docker >/dev/null
command -v curl >/dev/null
command -v python3 >/dev/null

containers=(job-portal-discovery job-portal-config-server job-portal-gateway
  job-portal-user-service job-portal-company-service job-portal-job-service
  job-portal-resume-service job-portal-application-service job-portal-preference-service
  job-portal-ai-service job-portal-notification-service job-portal-redis
  job-portal-userdb job-portal-companydb job-portal-jobdb job-portal-resumedb
  job-portal-applicationdb job-portal-preferencedb job-portal-mailhog)
for container in "${containers[@]}" "${KAFKA_CONTAINER:-job-portal-kafka-dev}"; do
  state=$(docker inspect --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' "$container")
  printf '%s: %s\n' "$container" "$state"
  [[ "$state" == running* && "$state" != *unhealthy* && "$state" != *starting* ]]
done

for service in user company job resume application preference; do
  docker exec "job-portal-${service}db" pg_isready -U postgres
done
[[ $(docker exec job-portal-redis redis-cli ping) == PONG ]]
curl --fail --silent --show-error --max-time 15 http://127.0.0.1:8888/job-portal-user-service/default |
  python3 -c 'import json,sys; assert json.load(sys.stdin).get("propertySources"), "Config Server returned no property sources"'
curl --fail --silent --show-error --max-time 15 -H 'Accept: application/json' http://127.0.0.1:8761/eureka/apps |
  python3 -c '
import json, sys
apps = json.load(sys.stdin)["applications"].get("application", [])
if isinstance(apps, dict): apps = [apps]
up = set()
for app in apps:
    instances = app.get("instance", [])
    if isinstance(instances, dict): instances = [instances]
    if any(i.get("status") == "UP" for i in instances): up.add(app["name"].lower())
required = {"job-portal-" + s + "-service" for s in ("user", "company", "job", "resume", "application", "preference", "ai", "notification")}
assert required <= up, "Missing UP registrations: " + str(sorted(required - up))
print("All eight business services registered UP")'
docker exec "${KAFKA_CONTAINER:-job-portal-kafka-dev}" /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 >/dev/null
docker exec "${KAFKA_CONTAINER:-job-portal-kafka-dev}" /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group "${NOTIFICATION_GROUP:-notification-service}" --state |
  python3 -c 'import re,sys; text=sys.stdin.read(); print(text); assert re.search(r"\bStable\s+[1-9][0-9]*\s*$", text, re.M), "Notification consumer group is not stable with active members"'
curl --fail --silent --show-error --max-time 15 'http://127.0.0.1:8025/api/v2/messages?start=0&limit=1' |
  python3 -c 'import json,sys; assert isinstance(json.load(sys.stdin).get("items"), list)'
printf 'Infrastructure preflight passed. Run run.mjs to verify business operations, live AI, and delivery.\n'
