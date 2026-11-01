#!/usr/bin/env bash
# Лаб. №5, Експеримент 1: Reliability Test засобу статичного аналізу (tools/ComplexityMeter.java).
# Той самий файл -> різні умови оточення (кінці рядків / BOM / кодування / версія JDK) -> порівняння метрик.
#
# Запуск з КОРЕНЯ проєкту (Git Bash / Linux / macOS):
#   bash scripts/lab5/reliability.sh                      # локальний java + Docker-образи JDK 17/21/23
#   USE_DOCKER=0 bash scripts/lab5/reliability.sh         # лише локальний java
#   JDKS="17 21" bash scripts/lab5/reliability.sh <шлях/до/Файла.java>
set -u
FILE="${1:-src/main/java/lab2/resilience/ResilientPricingClient.java}"
JDKS="${JDKS:-17 21 23}"
USE_DOCKER="${USE_DOCKER:-1}"
OUT="build/lab5/reliability"
NAME="$(basename "$FILE")"
WINPWD="$(pwd -W 2>/dev/null || pwd)"      # Git Bash -> C:/..., Linux -> звичайний pwd

[ -f "$FILE" ] || { echo "Файл не знайдено: $FILE"; exit 2; }
rm -rf "$OUT"; mkdir -p "$OUT" docs/lab5

# ---- 1. Варіанти файла (бізнес-логіка однакова, змінюється лише "оточення") ----
mkdir -p "$OUT/lf" "$OUT/crlf" "$OUT/bom" "$OUT/cp1251"
tr -d '\r' < "$FILE"                                  > "$OUT/lf/$NAME"
tr -d '\r' < "$FILE" | sed 's/$/\r/'                  > "$OUT/crlf/$NAME"
{ printf '\xEF\xBB\xBF'; tr -d '\r' < "$FILE"; }      > "$OUT/bom/$NAME"
if command -v iconv >/dev/null 2>&1; then
  tr -d '\r' < "$FILE" | iconv -f UTF-8 -t CP1251 2>/dev/null > "$OUT/cp1251/$NAME" || rm -rf "$OUT/cp1251"
else
  rm -rf "$OUT/cp1251"
fi
VARIANTS="lf crlf bom"; [ -d "$OUT/cp1251" ] && VARIANTS="$VARIANTS cp1251"

# ---- 2. Раннери ----
RUNNERS="local"
if [ "$USE_DOCKER" = "1" ] && command -v docker >/dev/null 2>&1; then
  for v in $JDKS; do RUNNERS="$RUNNERS jdk$v"; done
fi

run_meter() {   # $1=runner $2=variant
  local runner="$1" variant="$2" f="$OUT/$2/$NAME" raw="$OUT/raw_$1_$2.txt"
  if [ "$runner" = local ]; then
    java -Dstdout.encoding=UTF-8 tools/ComplexityMeter.java "$f" >"$raw" 2>&1
  else
    MSYS_NO_PATHCONV=1 docker run --rm -v "$WINPWD:/w" -w /w "eclipse-temurin:${runner#jdk}-jdk" \
      java -Dstdout.encoding=UTF-8 tools/ComplexityMeter.java "$f" >"$raw" 2>&1
  fi
}

CSV="docs/lab5/reliability-results.csv"
MD="docs/lab5/reliability-results.md"
echo "runner,variant,exit,bytes,sha256_8,loc_physical,loc_nonblank,javac_errors,getPrice_lines,getPrice_cyclo,getPrice_cogn,methods,max_cogn,max_cyclo,sum_cogn,sum_cyclo" > "$CSV"
JAVAVER="$(java -version 2>&1 | head -1)"

BASE=""
{
  echo "# Reliability Report (автогенерація $(date '+%F %T'))"
  echo; echo "Файл: \`$FILE\` · локальний java: \`$JAVAVER\`"; echo
  echo "| Раннер | Варіант | exit | байт | SHA-256 (8) | LOC фіз. | LOC непорожніх | javac-помилки у логу | getPrice: Lines/Cyclo/Cogn | Клас: methods / max Cogn / max Cyclo / Σ Cogn / Σ Cyclo | = еталон? |"
  echo "|---|---|---|---|---|---|---|---|---|---|---|"
} > "$MD"

for r in $RUNNERS; do
  for v in $VARIANTS; do
    run_meter "$r" "$v"; code=$?
    f="$OUT/$v/$NAME"; raw="$OUT/raw_${r}_$v.txt"
    bytes=$(wc -c < "$f" | tr -d ' ')
    sha=$(sha256sum "$f" | cut -c1-8)
    locp=$(wc -l < "$f" | tr -d ' ')
    locn=$(grep -cv '^[[:space:]]*$' "$f")
    diag=$(grep -c ': error:' "$raw")
    row=$(grep 'getPrice()' "$raw" | head -1)
    sumline=$(grep '^Methods:' "$raw" | head -1)
    if [ -n "$row" ] && [ -n "$sumline" ]; then
      gl=$(echo "$row" | awk '{print $(NF-2)}'); gcy=$(echo "$row" | awk '{print $(NF-1)}'); gco=$(echo "$row" | awk '{print $NF}')
      read -r m mc my sc sy <<<"$(echo "$sumline" | sed -E 's/Methods: ([0-9]+) \| max Cognitive: ([0-9]+) \| max Cyclomatic: ([0-9]+) \| sum Cognitive: ([0-9]+) \| sum Cyclomatic: ([0-9]+)/\1 \2 \3 \4 \5/')"
      sig="$gl/$gcy/$gco|$m/$mc/$my/$sc/$sy"
      [ -z "$BASE" ] && BASE="$sig"
      if [ "$sig" = "$BASE" ]; then same="✅ так"; else same="❌ НІ"; fi
      echo "$r,$v,$code,$bytes,$sha,$locp,$locn,$diag,$gl,$gcy,$gco,$m,$mc,$my,$sc,$sy" >> "$CSV"
      echo "| $r | $v | $code | $bytes | $sha | $locp | $locn | $diag | $gl / $gcy / $gco | $m / $mc / $my / $sc / $sy | $same |" >> "$MD"
    else
      err=$(grep -m1 -iE 'error|exception' "$raw" | cut -c1-90 | tr '|' '/')
      echo "$r,$v,$code,$bytes,$sha,$locp,$locn,$diag,ERROR,,,,,,," >> "$CSV"
      echo "| $r | $v | $code | $bytes | $sha | $locp | $locn | $diag | **ПОМИЛКА** | ${err:-див. $raw} | ❌ |" >> "$MD"
    fi
  done
done

echo; cat "$MD"
echo; echo "Сирі виводи: $OUT/raw_*.txt   |   CSV: $CSV   |   Markdown: $MD"
