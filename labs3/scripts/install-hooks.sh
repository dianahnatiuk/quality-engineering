#!/bin/sh
# Підключає версіонований pre-commit hook із scripts/hooks
git config core.hooksPath scripts/hooks
chmod +x scripts/hooks/pre-commit
echo "Hook встановлено: core.hooksPath = scripts/hooks"
