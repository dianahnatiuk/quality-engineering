#!/usr/bin/env bash
# Хук для make-lab8-commits.sh:  LAB8_AFTER_APPLY='bash scripts/lab8/after-stage.sh' bash scripts/lab8/make-lab8-commits.sh
# Для кожного стану: збірка + лог компіляції + метрики (CkMetrics). Sonar-сканування додай сам (sonar-scanner), якщо сервер запущено.
S="${LAB8_STAGE:?}"; mkdir -p docs/lab8/logs docs/lab8/metrics
if mvn -q -DskipTests compile > "docs/lab8/logs/$S-compile.log" 2>&1; then
  java tools/CkMetrics.java target/classes lab8 --json "docs/lab8/metrics/$S.json" | tee "docs/lab8/metrics/$S.txt"
else
  echo "[$S] компіляція ЗЛАМАНА (очікувано для FAIL-стану) — лог: docs/lab8/logs/$S-compile.log"; tail -n 20 "docs/lab8/logs/$S-compile.log"
fi
