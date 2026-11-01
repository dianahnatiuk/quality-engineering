@echo off
REM Ручний запуск secret scanner.  scripts\secret-scan.cmd staged   - лише staged-зміни
REM                              scripts\secret-scan.cmd history  - уся історія комітів
if "%1"=="history" (
  gitleaks git --config .gitleaks.toml --redact --verbose
) else (
  gitleaks git --pre-commit --staged --config .gitleaks.toml --redact --verbose
)
exit /b %ERRORLEVEL%
