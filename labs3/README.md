# Лабораторна №3: Maintainability Refactoring та Software Supply Chain Security

Навчальний сервіс замовлень (`GET /api/order/{id}` + зовнішня залежність Pricing Service) із Лабораторної №2,
розширений для Лабораторної №3. Стек: **Java 21+, Maven, JUnit 5, Log4j 2**.

## Вимоги
| Інструмент | Для чого | Встановлення |
|---|---|---|
| JDK 21+ , Maven 3.9+ | збірка і тести | у вас вже є JDK 23; Maven — вбудований в IntelliJ або окремо |
| Git | історія, hook | https://git-scm.com |
| **OSV-Scanner** | SCA (залежності) | реліз `google/osv-scanner` на GitHub (`osv-scanner_windows_amd64.exe` → у PATH), або `scoop install osv-scanner` |
| **Gitleaks** | пошук секретів | реліз `gitleaks/gitleaks` на GitHub (zip для Windows → у PATH), або `scoop install gitleaks` / `winget search gitleaks` |

## Структура
```
pom.xml                          Maven: Log4j (direct), JUnit 5 (test)
src/main/java/lab2/...           сервіси, resilience, loadtest, config
src/test/java/lab2/...           автоматизовані тести (JUnit 5)
tools/ComplexityMeter.java       аналізатор складності (Cognitive + Cyclomatic), quality gate
tools/SecretTrapDemo.java        краш-тест для secret scanner (синтетичний секрет)
scripts/sca-audit.*              SCA-аудит (OSV-Scanner + dependency:tree)
scripts/hooks/pre-commit         hook: Gitleaks перевіряє staged-зміни
scripts/install-hooks.*          одноразове підключення hook
scripts/secret-scan.cmd          ручний запуск (staged / history)
.gitleaks.toml                   правила сканера (стандартні + власне)
.env.example                     шаблон конфігурації БЕЗ значень (.env — в .gitignore)
docs/metrics/before|after.txt    метрики складності до/після
docs/sca/TRIAGE.md               профіль ризиків і CVE-тріаж
docs/DEMO.md                     сценарій захисту на 5 хвилин
```

## Git-теги (стани для демонстрації)
| Тег | Стан |
|---|---|
| `refactor-before` | вихідний код + захисні тести + baseline-метрики |
| `refactor-after`  | після рефакторингу `LoadTest.run()` |
| `sca-before`      | залежність `log4j-core 2.14.1` (вразлива) |
| `sca-after`       | `log4j 2.24.3` |
| `secrets-ready`   | secret scanner, hook і безпечна конфігурація налаштовані |

## Команди
```
mvn test                                                         # усі тести
java -Dstdout.encoding=UTF-8 tools/ComplexityMeter.java src/main/java            # метрики складності
java -Dstdout.encoding=UTF-8 tools/ComplexityMeter.java src/main/java --fail-over # quality gate (код 1, якщо Cognitive>15 або Cyclomatic>10)
scripts\sca-audit.cmd                                            # SCA (Windows);  sh scripts/sca-audit.sh (Linux/macOS)
scripts\install-hooks.cmd                                        # підключити pre-commit hook (один раз)
scripts\secret-scan.cmd staged | history                         # ручна перевірка секретів
```

**Увага:** тести `LoadTestCharacterizationTest` піднімають сервіси на портах 9000/9001 — зупиніть
`ExternalService`/`MainService` у IDE перед `mvn test`.

## Змінні середовища
`PRICING_API_KEY` — ключ клієнта Pricing Service. Скопіюйте `.env.example` у `.env` і впишіть значення
(файл ігнорується git). У IntelliJ: Run Configuration → *Environment variables* → вибрати `.env`.
