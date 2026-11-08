#!/usr/bin/env bash
# ЛР8, Завд.1: програмний видобуток метрик через SonarQube REST API.
#   SONAR_URL=http://localhost:9000 SONAR_TOKEN=... PROJECT_KEY=labs3 \
#   bash scripts/lab8/sonar-ck.sh [шлях/до/файлу.java ...]
# Без аргументів береться Core Domain / Infrastructure / DTO з пакета lab8 (заміни на файли з ЛР6, якщо треба).
set -uo pipefail
SONAR_URL="${SONAR_URL:-http://localhost:9000}"
: "${SONAR_TOKEN:?встанови SONAR_TOKEN}"
: "${PROJECT_KEY:?встанови PROJECT_KEY}"
OUT="${OUT_DIR:-docs/lab8/sonar}"; mkdir -p "$OUT"

FILES=("$@")
[ ${#FILES[@]} -eq 0 ] && FILES=(
  src/main/java/lab8/legacy/OrderProcessor.java    # Core Domain
  src/main/java/lab8/infra/PaymentGateway.java     # Infrastructure
  src/main/java/lab8/model/Order.java              # Utility / DTO
)

# метрики з методички + ті, що реально існують у Sonar (complexity, cognitive_complexity, ...)
WANTED="class_complexity complexity cognitive_complexity coupling_between_objects lcom4 cohesion_in_methods depth_in_tree ncloc functions classes"

# 1) які метрики сервер реально віддає (інакше api/measures/component поверне 404 "Metric ... not found")
curl -s -u "$SONAR_TOKEN:" "$SONAR_URL/api/metrics/search?ps=500" -o "$OUT/metrics-available.json"
AVAILABLE="$(grep -o '"key":"[^"]*"' "$OUT/metrics-available.json" | cut -d'"' -f4)"
KEYS=""; MISSING=""
for k in $WANTED; do
  if printf '%s\n' "$AVAILABLE" | grep -qx "$k"; then KEYS="$KEYS$k,"; else MISSING="$MISSING$k "; fi
done
KEYS="${KEYS%,}"
echo "metricKeys    : $KEYS"            | tee "$OUT/requests.log"
echo "не віддає сервер: $MISSING"       | tee -a "$OUT/requests.log"
echo "(для них використовуй: java tools/CkMetrics.java target/classes lab8 --json ...)" | tee -a "$OUT/requests.log"

# 2) по файлах
for f in "${FILES[@]}"; do
  f="${f%%#*}"; f="$(echo "$f" | xargs)"; [ -z "$f" ] && continue
  url="$SONAR_URL/api/measures/component?component=${PROJECT_KEY}:${f}&metricKeys=${KEYS}"
  echo "GET $url" | tee -a "$OUT/requests.log"
  curl -s -u "$SONAR_TOKEN:" "$url" -o "$OUT/$(echo "$f" | tr '/' '_').json"
  cat "$OUT/$(echo "$f" | tr '/' '_').json"; echo
done

# 3) рівень проєкту
url="$SONAR_URL/api/measures/component?component=${PROJECT_KEY}&metricKeys=${KEYS}"
echo "GET $url" | tee -a "$OUT/requests.log"
curl -s -u "$SONAR_TOKEN:" "$url" -o "$OUT/project.json"; cat "$OUT/project.json"; echo
