@echo off
REM Підключає версіонований pre-commit hook із scripts\hooks
git config core.hooksPath scripts/hooks
echo Hook встановлено: core.hooksPath = scripts/hooks
