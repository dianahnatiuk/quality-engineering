#!/usr/bin/env bash
# Лаб. №5, Експеримент 2: Stability Test динамічних метрик (p95, Error Rate, RAM).
# Той самий код, той самий тест — змінюється лише "інфраструктурний шум".
#
# Запуск з КОРЕНЯ проєкту (Git Bash + Docker Desktop запущений):
#   bash scripts/lab5/stability.sh
# Параметри (змінні оточення):
#   REPS=3  VUS=100  DURATION=10  CPUS_FULL=2  CPUS_LOW=0.5  NOISE_PROCS=4  WITH_MEM=0
# Калібрування: якщо сценарій A (ізольований) сам дає FAIL — зменште VUS; якщо C не псує метрики — збільште NOISE_PROCS
# або зменшіть CPUS_NOISE. Зафіксуйте підібрані параметри у звіті.
set -u
IMG="lab5-stability"
REPS="${REPS:-3}"; VUS="${VUS:-100}"; DURATION="${DURATION:-10}"
CPUS_FULL="${CPUS_FULL:-2}"; CPUS_LOW="${CPUS_LOW:-0.5}"; CPUS_NOISE="${CPUS_NOISE:-1}"
NOISE_PROCS="${NOISE_PROCS:-4}"; WITH_MEM="${WITH_MEM:-0}"
CSV="docs/lab5/stability-results.csv"
MD="docs/lab5/stability-results.md"

command -v docker >/dev/null 2>&1 || { echo "Docker не знайдено"; exit 2; }
mkdir -p docs/lab5
docker build -q -f scripts/lab5/Dockerfile.stability -t "$IMG" . || { echo "docker build FAILED"; exit 1; }

echo "scenario,rep,cpus,memory,noise_procs,vus,n,rps,avg_ms,p50_ms,p90_ms,p95_ms,p99_ms,error_pct,result,peak_mem_mb" > "$CSV"

run() {   # scenario cpus memory noise
  local sc="$1" cpus="$2" mem="$3" noise="$4" out line
  for i in $(seq 1 "$REPS"); do
    echo ">>> $sc  (повтор $i/$REPS, --cpus=$cpus --memory=$mem, шум=$noise)"
    out=$(MSYS_NO_PATHCONV=1 docker run --rm --cpus="$cpus" --memory="$mem" \
          -e LABEL="$sc" -e NOISE="$noise" -e VUS="$VUS" -e DURATION="$DURATION" "$IMG" 2>&1)
    line=$(echo "$out" | grep '^CSV,' | tail -1)
    if [ -n "$line" ]; then
      echo "$line" | sed -E "s/^CSV,[^,]*,[^,]*,/$sc,$i,$cpus,$mem,$noise,$VUS,/" >> "$CSV"
    else
      echo "$sc,$i,$cpus,$mem,$noise,$VUS,,,,,,,,,NO_RESULT(OOM/crash),"  >> "$CSV"
    fi
  done
}

run "A_isolated"      "$CPUS_FULL"  "1g"  0
run "B_cpu_limited"   "$CPUS_LOW"   "1g"  0
run "C_noisy_neighbor" "$CPUS_NOISE" "1g" "$NOISE_PROCS"
[ "$WITH_MEM" = "1" ] && run "D_ram_limited" "$CPUS_FULL" "350m" 0

# ---- Markdown-таблиця: медіана по повторах + кількість FAIL ----
python3 - "$CSV" "$MD" <<'PY' 2>/dev/null || echo "(python3 не знайдено — використайте $CSV напряму)"
import csv, sys, statistics as st
rows = list(csv.DictReader(open(sys.argv[1], encoding="utf-8")))
sc = {}
for r in rows: sc.setdefault(r["scenario"], []).append(r)
def med(rs, k):
    v = [float(r[k]) for r in rs if r[k] not in ("", None)]
    return f"{st.median(v):.1f}" if v else "—"
out = ["| Сценарій | --cpus | --memory | шум | p95, мс (медіана) | p95 мін–макс | Error Rate % (медіана) | RPS (медіана) | RAM пік, МБ | FAIL з N |", "|---|---|---|---|---|---|---|---|---|---|"]
for k, rs in sc.items():
    p95 = [float(r["p95_ms"]) for r in rs if r["p95_ms"]]
    rng = f"{min(p95):.0f}–{max(p95):.0f}" if p95 else "—"
    fails = sum(1 for r in rs if r["result"] != "PASS")
    out.append(f"| {k} | {rs[0]['cpus']} | {rs[0]['memory']} | {rs[0]['noise_procs']} | {med(rs,'p95_ms')} | {rng} | {med(rs,'error_pct')} | {med(rs,'rps')} | {med(rs,'peak_mem_mb')} | {fails} з {len(rs)} |")
open(sys.argv[2], "w", encoding="utf-8").write("\n".join(out) + "\n")
print("\n".join(out))
PY
echo; echo "CSV: $CSV | Markdown: $MD"
