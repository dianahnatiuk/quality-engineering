#!/usr/bin/env bash
# Ізольований і відтворюваний статичний аналіз: фіксований образ JDK + жорсткі ліміти ресурсів раннера.
# Запуск з кореня проєкту:  bash scripts/lab5/analyze-in-docker.sh [--fail-over]
# Той самий образ/ліміти використовуються локально і в CI -> числа не залежать від "заліза" та версії JDK на машині.
set -eu
IMAGE="eclipse-temurin:21-jdk"          # зафіксована версія інструмента (не :latest)
WINPWD="$(pwd -W 2>/dev/null || pwd)"
MSYS_NO_PATHCONV=1 docker run --rm --cpus=1 --memory=512m --network=none \
  -v "$WINPWD:/w:ro" -w /w "$IMAGE" \
  java -Dstdout.encoding=UTF-8 tools/ComplexityMeter.java src/main/java "$@"
