# Сценарій захисту (5 хвилин)

Усі інструменти встановлено заздалегідь: `mvn`, `osv-scanner`, `gitleaks`; hook підключено (`scripts\install-hooks.cmd`).
Повернутися на робочу гілку після `git checkout <tag>`: `git checkout main`.

## Крок 1. Measurable Refactoring Diff (~1,5 хв)
```
git log --oneline
git diff refactor-before refactor-after --stat
git checkout refactor-before
java -Dstdout.encoding=UTF-8 tools/ComplexityMeter.java src/main/java --only LoadTest     # run(): Cognitive 25, Cyclomatic 11
git checkout main
java -Dstdout.encoding=UTF-8 tools/ComplexityMeter.java src/main/java --only LoadTest     # run(): Cognitive 0, max у класі 3
mvn test                                                                                  # 15 тестів PASS
```
Техніки: **Extract Method** (run → generateLoad / runVirtualUser / sendRequest / summarize / printReport),
**Introduce Parameter Object** (`Recorder`, `Measurements`), **Guard Clauses** (`pauseForThinkTime`, `errorRate`, `throughput`, `percentile`).
Поведінку не змінено: характеризаційні тести `LoadTestCharacterizationTest` писались ДО змін і пройшли без правок.

## Крок 2. SCA Security Audit (~1,5 хв)
```
git checkout sca-before
scripts\sca-audit.cmd          # log4j-core 2.14.1 -> Critical CVE-2021-44228 (див. docs/sca/TRIAGE.md)
git checkout main
scripts\sca-audit.cmd          # після оновлення до 2.24.3
mvn test
```
Тріаж: компонент, версія, direct/transitive, severity, виправлена версія, досяжність (`itemId` з URL потрапляє в лог).

## Крок 3. Secrets Trap (~1,5 хв)
```
java tools/SecretTrapDemo.java create
git add src/main/java/lab2/config/LegacyPricingSettings.java
git commit -m "demo: legacy settings"            # hook -> FAIL, коміт заблоковано (файл, рядок, правило)
git restore --staged src/main/java/lab2/config/LegacyPricingSettings.java
java tools/SecretTrapDemo.java fix              # секрет -> змінна середовища + локальний .env
git check-ignore -v .env                        # .env ігнорується
git add -A
git commit -m "fix: read pricing key from environment"   # hook -> PASS
scripts\secret-scan.cmd history                 # секрету в історії немає
```
Після демонстрації повернути чистий стан: `git reset --hard secrets-ready` і видалити `.env`.

## Типові запитання
- **Cyclomatic vs Cognitive?** Cyclomatic рахує незалежні шляхи (кожна гілка/цикл/&&/||, +1) і не враховує
  вкладеність; Cognitive оцінює складність *читання*: штрафує вкладеність (цикл у лямбді всередині циклу — дорожче)
  і не штрафує послідовні однотипні конструкції. Наш `run()` мав 25 через вкладені `for → lambda → while → try/if`.
- **Чому зріс сумарний Cyclomatic (18 → 30)?** Кожен новий метод має базове значення 1; важить максимум на метод
  (11 → 4) і Cognitive (сума 30 → 15, максимум 25 → 3).
- **Що таке reachability CVE?** Чи викликається вразливий код у нашому проєкті: у нас лог містить `itemId` з URL → досяжно.
- **Чому не `ignore`?** Без досяжності, строку дії і компенсувального контролю — штраф; ми оновили залежність.
- **Чому hook, а не лише .gitignore?** `.gitignore` не ловить секрет, уже вставлений у код; hook перевіряє вміст staged-змін.
