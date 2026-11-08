#!/usr/bin/env bash
# ЛР8: відтворює 6 комітів (Baseline -> FAIL -> PASS для Завд.2 і Завд.3) з оверлеїв у scripts/lab8/stages.
# Запуск з кореня репозиторію (Git Bash на Windows теж підходить):  bash scripts/lab8/make-lab8-commits.sh
# Опційно: LAB8_AFTER_APPLY='bash scripts/lab8/after-stage.sh' — команда, що виконується після застосування кожного стану
#          (напр. mvn compile + sonar-scanner + збір метрик) ПЕРЕД комітом.
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"
STAGES="scripts/lab8/stages"

if [ -d src/main/java/lab8 ] && [ -n "$(git status --porcelain src/main/java/lab8 src/test/java/lab8 2>/dev/null)" ]; then
  echo "src/*/lab8 вже має незакомічені зміни — прибери їх перед запуском." >&2
  exit 1
fi

for dir in $(ls -d "$STAGES"/*/ | sort); do
  name="$(basename "$dir")"
  echo "=== $name ==="

  # 1) накладаємо файли стану
  cp -R "$dir/files/." "$ROOT/"

  # 2) видаляємо файли, яких у цьому стані вже немає
  if [ -f "$dir/DELETE" ]; then
    while IFS= read -r f; do
      [ -n "$f" ] && rm -f "$f"
    done < "$dir/DELETE"
  fi

  # 3) (необов'язково) компіляція / сканування / метрики для цього стану
  if [ -n "${LAB8_AFTER_APPLY:-}" ]; then
    LAB8_STAGE="$name" bash -c "$LAB8_AFTER_APPLY" || echo "(after-apply завершився з помилкою — для FAIL-станів це очікувано)"
  fi

  # 4) коміт; FAIL-стани навмисно некомпілюються, тому обходимо pre-commit хук
  git add -A src/main/java/lab8 src/test/java/lab8
  for p in docs/lab8/metrics docs/lab8/logs; do [ -e "$p" ] && git add -A "$p"; done
  flags=(--no-verify)
  [ -f "$dir/NOVERIFY" ] && flags+=(--no-verify)
  git commit "${flags[@]}" -m "$(cat "$dir/MESSAGE")"
done

echo
git --no-pager log --oneline --graph -n 8
