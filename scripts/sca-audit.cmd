@echo off
REM ============================================================
REM  SCA-аудит залежностей (Software Composition Analysis)
REM  Інструмент: OSV-Scanner (google/osv-scanner) + Maven dependency:tree
REM  Запуск з кореня проєкту:   scripts\sca-audit.cmd
REM  Код завершення: 0 = вразливостей немає, 1 = знайдено вразливості
REM ============================================================
setlocal
if not exist reports mkdir reports


echo [1/3] Dependency tree (direct / transitive):\
call mvn -q dependency:tree -DoutputFile=reports\dependency-tree.txt\
type reports\dependency-tree.txt\
\
echo.\
echo [2/3] Scanning pom.xml (OSV-Scanner):\
osv-scanner scan source -L pom.xml\
set RC=%ERRORLEVEL%\
if %RC% GEQ *127&#x20;*(\
&#x20; echo New syntax is unavailable, trying compatibility mode for older versions...\
&#x20; osv-scanner --lockfile=pom.xml\
&#x20; set RC=%ERRORLEVEL%\
)

echo.
echo [3/3] Machine-readable report: reports\osv-report.json
osv-scanner scan source -L pom.xml --format json > reports\osv-report.json 2>nul

echo.
if %RC%==0 (echo SCA STATUS: PASS - no vulnerabilities detected) else (echo SCA STATUS: FAIL - vulnerabilities detected, exit code %RC%)
exit /b %RC%
