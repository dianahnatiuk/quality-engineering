@echo off
REM Ручний запуск secret scanner (працює з будь-якої папки).
REM   labs3\scripts\secret-scan.cmd staged   - лише staged-зміни
REM   labs3\scripts\secret-scan.cmd history  - уся історія комітів
cd /d "%~dp0.."
if "%1"=="history" (
  gitleaks git --config .gitleaks.toml --redact --verbose
) else (
  gitleaks git --pre-commit --staged --config .gitleaks.toml --redact --verbose
)
exit /b %ERRORLEVEL%
