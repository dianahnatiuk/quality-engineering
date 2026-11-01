#!/usr/bin/env bash
# Виконується ВСЕРЕДИНІ контейнера (див. Dockerfile.stability).
# Піднімає ExternalService (9001) + MainService (9000), робить прогрів, опційно запускає "шум"
# (CPU-хогів у тому ж cgroup), вимірює піковий RAM контейнера і запускає LoadTest.
# В кінці друкує один рядок:  CSV,<label>,<vus>,<n>,<rps>,<avg>,<p50>,<p90>,<p95>,<p99>,<errorRate%>,<PASS|FAIL>,<peakMemMB>
set -u
VUS="${VUS:-100}"; DURATION="${DURATION:-10}"; LABEL="${LABEL:-run}"; NOISE="${NOISE:-0}"
CP="target/classes:target/lib/*"
export PRICING_API_KEY="${PRICING_API_KEY:-lab5-dummy-not-a-secret}"

cleanup() { kill $(jobs -p) 2>/dev/null; }
trap cleanup EXIT

java -cp "$CP" lab2.service.ExternalService >/tmp/external.log 2>&1 &
java -cp "$CP" lab2.service.MainService     >/tmp/main.log     2>&1 &

for _ in $(seq 1 150); do   # до 30 с очікування порту 9000
  (echo > /dev/tcp/127.0.0.1/9000) 2>/dev/null && break
  sleep 0.2
done
(echo > /dev/tcp/127.0.0.1/9000) 2>/dev/null || { echo "CSV,$LABEL,$VUS,NO_START,,,,,,,,,"; cat /tmp/main.log /tmp/external.log >&2; exit 1; }

# прогрів JIT (результат відкидаємо)
java -cp "$CP" lab2.loadtest.LoadTest 5 2 warmup >/dev/null 2>&1

# шум: N CPU-хогів у тому самому контейнері (конкурують за ліміт --cpus з JVM)
for _ in $(seq 1 "$NOISE"); do ( while :; do :; done ) & done

# піковий RAM контейнера (cgroup v2 -> v1)
memfile=/sys/fs/cgroup/memory.current; [ -r "$memfile" ] || memfile=/sys/fs/cgroup/memory/memory.usage_in_bytes
( max=0; while :; do v=$(cat "$memfile" 2>/dev/null || echo 0); [ "$v" -gt "$max" ] && max=$v; echo "$max" > /tmp/peakmem; sleep 0.3; done ) &

java -cp "$CP" lab2.loadtest.LoadTest "$VUS" "$DURATION" "$LABEL" | tee /tmp/loadtest.txt

get() { grep -m1 "$1" /tmp/loadtest.txt | sed -E "$2"; }
n=$(get 'Requests total' 's/.*: *([0-9]+).*/\1/')
rps=$(get 'RPS' 's/.*: *([0-9.]+).*/\1/')
avg=$(get 'avg latency' 's/.*: *([0-9.]+) ms.*/\1/')
p50=$(get '^p50' 's/.*: *([0-9.]+) ms.*/\1/')
p90=$(get '^p90' 's/.*: *([0-9.]+) ms.*/\1/')
p95=$(get '^p95' 's/.*: *([0-9.]+) ms.*/\1/')
p99=$(get '^p99' 's/.*: *([0-9.]+) ms.*/\1/')
err=$(get 'Error Rate' 's/.*: *([0-9.]+) %.*/\1/')
res=$(get 'RESULT' 's/.*: *(PASS|FAIL).*/\1/')
peak=$(( $(cat /tmp/peakmem 2>/dev/null || echo 0) / 1048576 ))
echo "CSV,$LABEL,$VUS,$n,$rps,$avg,$p50,$p90,$p95,$p99,$err,$res,$peak"
