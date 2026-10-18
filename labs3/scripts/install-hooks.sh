#!/bin/sh
# Підключає pre-commit hook із labs3/scripts/hooks (шлях відносно кореня репозиторію)
git config core.hooksPath labs3/scripts/hooks
chmod +x labs3/scripts/hooks/pre-commit 2>/dev/null
echo "Hook встановлено: core.hooksPath = labs3/scripts/hooks"
echo "Вимкнути:  git config --unset core.hooksPath"
