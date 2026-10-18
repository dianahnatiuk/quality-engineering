@echo off
REM ============================================================
REM  SCA-аудит залежностей (Software Composition Analysis)
REM  Інструмент: OSV-Scanner (google/osv-scanner) + Maven dependency:tree
REM  Запуск з кореня проєкту:   scripts\sca-audit.cmd
REM  Код завершення: 0 = вразливостей немає, 1 = знайдено вразливості
REM ============================================================
setlocal
cd /d "%~dp0.."
if not exist reports mkdir reports

echo [1/3] Дерево залежностей (direct / transitive):
call mvn -q dependency:tree -DoutputFile=reports\dependency-tree.txt
type reports\dependency-tree.txt

echo.
echo [2/3] Сканування pom.xml (OSV-Scanner):
osv-scanner scan source -L pom.xml
set RC=%ERRORLEVEL%
if %RC% GEQ 127 (
  echo Новий синтаксис недоступний, пробуємо сумісний режим старіших версій...
  osv-scanner --lockfile=pom.xml
  set RC=%ERRORLEVEL%
)

echo.
echo [3/3] Машинозчитуваний звіт: reports\osv-report.json
osv-scanner scan source -L pom.xml --format json > reports\osv-report.json 2>nul

echo.
if %RC%==0 (echo SCA STATUS: PASS - вразливостей не виявлено) else (echo SCA STATUS: FAIL - виявлено вразливості, код %RC%)
exit /b %RC%
