# ЛР8 — пакет файлів (Java 21, проєкт labs3)

## Що всередині
| Шлях | Призначення |
|---|---|
| `scripts/lab8/stages/01…06/` | 6 станів коду (Baseline→FAIL→PASS для Завд.2 та Завд.3) |
| `scripts/lab8/make-lab8-commits.sh` | накладає стани по черзі й робить 6 комітів з правильними повідомленнями |
| `scripts/lab8/sonar-ck.sh` / `.ps1` | Завд.1: curl / PowerShell-запити до SonarQube REST API |
| `scripts/lab8/sonar-project.properties.example` | приклад конфігу sonar-scanner |
| `tools/CkMetrics.java` | локальний підрахунок WMC/CBO/RFC/LCOM/DIT/PIM/NOO + MOOD (MHF/AHF/COF) |
| `docs/lab8/REPORT.md` | шаблон звіту з уже підставленими цифрами та логами |

## Порядок дій
1. Розпакуй архів у **корінь проєкту** (`labs3/`), щоб з'явились `scripts/lab8`, `tools`, `docs/lab8`.
2. Потрібно: Java 17+ (код використовує records), `pom.xml` із JUnit 5 (`org.junit.jupiter`).
3. Запусти SonarQube, зроби `sonar-scanner` (props — з `.example`).
4. Створи коміти (Git Bash):
   ```bash
   LAB8_AFTER_APPLY='bash scripts/lab8/after-stage.sh' bash scripts/lab8/make-lab8-commits.sh
   ```
   Для FAIL-станів коміт робиться з `--no-verify` (pre-commit хук не пропустить зламану збірку — це навмисно).
5. Між станами за бажанням запускай `sonar-scanner` і `scripts/lab8/sonar-ck.*` (скріншоти для звіту).
6. Допиши в `docs/lab8/REPORT.md` усі `TODO(студент)`.

Скрипт комітить лише `src/**`, `docs/lab8/metrics` та `docs/lab8/logs`; `tools/` і `scripts/` закоміть окремо
(`chore(lab8): add audit tooling`).
