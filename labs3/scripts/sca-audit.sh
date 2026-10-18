#!/bin/sh
# SCA-аудит залежностей: OSV-Scanner + Maven dependency:tree.
# Запуск з кореня проєкту:  sh scripts/sca-audit.sh
# Код завершення: 0 = вразливостей немає, 1 = знайдено вразливості.
mkdir -p reports

echo "[1/3] Дерево залежностей (direct / transitive):"
mvn -q dependency:tree -DoutputFile=reports/dependency-tree.txt && cat reports/dependency-tree.txt

echo
echo "[2/3] Сканування pom.xml (OSV-Scanner):"
osv-scanner scan source -L pom.xml
RC=$?
if [ "$RC" -ge 127 ]; then
  echo "Новий синтаксис недоступний, пробуємо сумісний режим старіших версій..."
  osv-scanner --lockfile=pom.xml
  RC=$?
fi

echo
echo "[3/3] Машинозчитуваний звіт: reports/osv-report.json"
osv-scanner scan source -L pom.xml --format json > reports/osv-report.json 2>/dev/null

echo
if [ "$RC" -eq 0 ]; then echo "SCA STATUS: PASS - вразливостей не виявлено"; else echo "SCA STATUS: FAIL - виявлено вразливості, код $RC"; fi
exit "$RC"
