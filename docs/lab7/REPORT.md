# Лабораторна робота №7: Інформаційна складність за Чепіним та Hotspots у CI/CD

> Об'єкт: `lab2.resilience.ResilientPricingClient` (інтеграційний контур: виклик зовнішнього Pricing Service).
> Місця для скріншотів позначені **[СКРІН: …]**; місця для ваших даних - **[ВСТАВИТИ: …]**.

---

## Завдання 1. Семантичний аудит за Чепіним

### 1.1 Декларація об'єкта аналізу

| Поле | Значення |
|---|---|
| Метод | `ResilientPricingClient.executeResilient(String itemId)` |
| Архітектурна роль | Інтеграційний контур: захищений виклик зовнішнього Pricing Service (retry + exponential backoff + fallback / graceful degradation) |
| Цикломатична складність (McCabe) | **CC = 5** (`while` +1, `catch` +1, `if attempts>1` +1, `if attempts<=maxRetries` +1, базова 1). Виміряно `tools/ComplexityMeter.java` |
| Cognitive Complexity | 8 |
| Доказ лінійності | CC ≤ 5 (критерій відбору виконано), при цьому метод оперує 16 змінними |

**[СКРІН: вивід `java tools/ComplexityMeter.java src/main/java --only ResilientPricingClient` ДО рефакторингу]**

### 1.2 Семантична матриця Чепіна (ДО)

Правило пріоритету: **C > M > P > T**. Окремою змінною рахується кожен параметр, локальна змінна, параметр `catch`, а також кожне поле `cfg.*` та колаборатор (`call`, `sleeper`, `log`), що читаються в тілі методу. Сам `cfg` окремо не рахується, бо розкладений на поля.

| № | Змінна | Тип даних | Клас | Інженерне обґрунтування | Вага |
|---|---|---|:-:|---|---:|
| 1 | `itemId` | String | P | Вхідний аргумент; читається в логах і передається в `call.call`. Мішана роль P+T, за пріоритетом P > T | 1 |
| 2 | `start` | long | T | Створюється лише щоб передати в `elapsedMs(start)` | 0.5 |
| 3 | `attempts` | int | **C** | Інкрементується (M), але ще й керує `while` та обома `if`. За пріоритетом C | 3 |
| 4 | `lastError` | String | M | Перезаписується в кожному `catch` | 2 |
| 5 | `delayMs` | double | M | Перезаписується `delayMs *= backoffMultiplier` | 2 |
| 6 | `price` | double | T | Результат виклику, без змін передається в `CallResult` | 0.5 |
| 7 | `e` | DependencyError | P | Лише читається (`e.getMessage()`) | 1 |
| 8 | `cfg.maxRetries` | int | **C** | Входить в умови `while` та `if` | 3 |
| 9 | `cfg.baseDelayMs` | long | P | Читається для ініціалізації `delayMs` | 1 |
| 10 | `cfg.maxDelayMs` | long | P | Читається в обчисленні `Math.min` | 1 |
| 11 | `cfg.backoffMultiplier` | double | P | Читається в обчисленні `delayMs *= …` | 1 |
| 12 | `cfg.requestTimeoutMs` | long | T | Без змін передається в `call.call` | 0.5 |
| 13 | `cfg.fallbackPrice` | double | T | Без змін передається в `CallResult` | 0.5 |
| 14 | `call` | DependencyCall | P | Колаборатор, лише читається | 1 |
| 15 | `sleeper` | Sleeper | P | Колаборатор, лише читається | 1 |
| 16 | `log` | Logger | P | Колаборатор, лише читається | 1 |

### 1.3 Розрахунок

Кількості: **P = 8**, **M = 2**, **C = 2**, **T = 4** (разом 16).

```
Cch = 1·P + 2·M + 3·C + 0.5·T
    = 1·8 + 2·2 + 3·2 + 0.5·4
    = 8 + 4 + 6 + 2
    = 20
```

**Cch = 20 > 10**: за Enterprise-порогом це «архітектурний хаос даних». Ризик: три змінні типу M (`attempts`, `lastError`, `delayMs`) живуть між ітераціями циклу; будь-який наступний розробник, що торкнеться хоча б одну з них, змінить поведінку retry для всього методу, а тести цього можуть не помітити (див. Завдання 2). Мак-Кейб (CC = 5) цього ризику не бачить.

---

## Завдання 2. Імутабельність та Value Objects

### 2.1 Симуляція збою (FAIL)

У код методу (`lab7-steps/step1-FAIL/ResilientPricingClient.java`) додано «хотфікс SLO»: скоротити паузу вдвічі, але прямим перезаписом змінної M:

```java
if (attempts <= cfg.maxRetries) {
    delayMs = delayMs * 0.5;            // ← перезапис M-змінної замість локальної копії
    sleeper.sleep((long) Math.min(delayMs, cfg.maxDelayMs));
    delayMs *= cfg.backoffMultiplier;
}
```

Краш-тест `BackoffMutationTest` перевіряє задокументований графік (`ResilienceConfig`: 50 + 100 + 200 = 350 мс):

| Тест | Очікувано | Фактично (FAIL) |
|---|---|---|
| `backoffFollowsDocumentedExponentialSchedule` | `[50, 100, 200]` | `[25, 25, 25]` |
| `totalWaitStaysAtDocumentedWorstCase350ms` | 350 | 75 |
| `pausesGrowStrictlyUntilCap_noConstantHammering` | `[50, 100, 200, 400]` | `[25, 25, 25, 25]` |

**Бізнес-наслідок:** експоненційний backoff зник (пауза стала сталою і втричі коротшою), клієнт довбить уже перевантажену залежність: retry storm. Усі 4 старі тести `ResilientPricingClientTest` при цьому **залишаються зеленими**, тобто мутація непомітна без цільового тесту.

**[СКРІН: червоний прогін `BackoffMutationTest` (IntelliJ або `mvn test -Dtest=BackoffMutationTest`)]**

Коміт: `feat(audit): simulate data mutation issue, FAIL`

### 2.2 Архітектурний рефакторинг (PASS)

**Імутабельність.** Усі локальні змінні та параметри оголошені `final`; цикл з лічильником замінено рекурсією, де наступна спроба = новий об'єкт (`ctx.next()`). Змінні M (`attempts`, `lastError`, `delayMs`) повністю ліквідовано: 2 → **0**.

**Value Objects (Java `record`, поля автоматично `final`):**

- `RetryPolicy(maxRetries, baseDelayMs, backoffMultiplier, maxDelayMs)`: 4 розрізнені `cfg.*` стали одним об'єктом. Містить чисті функції `allowsRetryAfter(n)` та `delayAfter(n)`; формула backoff `base·multiplier^(n−1)` обмежена `maxDelayMs` живе тут, а не розмазана по циклу.
- `RetryContext(policy, number, startNanos)`: замінює `attempts` + `start` (+ `delayMs` через політику). `canRetry()`, `isRetry()`, `backoffMs()`, `elapsedMs()`; перехід `next()` повертає новий об'єкт.

Умова «чи можна повторити» перенесена всередину VO: у методах клієнта вона зводиться до `ctx.canRetry()`.

### 2.3 Порівняльна матриця коду

**ДО** (мутабельний, 37 рядків, 16 змінних, 3 з них M):

```java
long start = System.nanoTime();
int attempts = 0;
String lastError = null;
double delayMs = cfg.baseDelayMs;
while (attempts <= cfg.maxRetries) {
    attempts++;
    try {
        double price = call.call(itemId, cfg.requestTimeoutMs);
        if (attempts > 1) { log.info(...); }
        return new CallResult(true, false, price, attempts, 200, "OK", elapsedMs(start));
    } catch (DependencyError e) {
        lastError = e.getMessage();
        log.warn(...);
        if (attempts <= cfg.maxRetries) {
            sleeper.sleep((long) Math.min(delayMs, cfg.maxDelayMs));
            delayMs *= cfg.backoffMultiplier;
        }
    }
}
log.error(...);
return new CallResult(true, true, cfg.fallbackPrice, attempts, 200, "degraded:fallback ...", elapsedMs(start));
```

**ПІСЛЯ** (незмінний; ключова частина):

```java
private CallResult executeResilient(String itemId) {
    return attempt(itemId, RetryContext.begin(RetryPolicy.from(cfg)));
}
private CallResult attempt(final String itemId, final RetryContext ctx) {
    try {
        return success(itemId, call.call(itemId, cfg.requestTimeoutMs), ctx);
    } catch (DependencyError e) {
        return failed(itemId, ctx, e);
    }
}
private CallResult failed(final String itemId, final RetryContext ctx, final DependencyError e) {
    log.warn("Attempt " + ctx.number() + "/" + ctx.maxAttempts() + " failed for " + itemId + ": " + e.getMessage());
    return ctx.canRetry() ? retry(itemId, ctx) : fallback(itemId, ctx, e.getMessage());
}
private CallResult retry(final String itemId, final RetryContext ctx) {
    sleeper.sleep(ctx.backoffMs());
    return attempt(itemId, ctx.next());
}
```
(`success` та `fallback` див. у `ResilientPricingClient.java`.) Тексти логів і `CallResult` збережено дослівно, публічний контракт `getPrice` не змінено.

### 2.4 Нова матриця Чепіна (ПІСЛЯ)

Той самий метод правил (C > M > P > T). Логіка розкладена на 6 блоків; кожен оцінено окремо.

**`executeResilient(itemId)`**: Cch = 1.0

| Змінна | Клас | Обґрунтування | Вага |
|---|:-:|---|---:|
| `itemId` | T | Без змін передається в `attempt` | 0.5 |
| `cfg` | T | Без змін передається в `RetryPolicy.from` | 0.5 |

**`attempt(itemId, ctx)`**: Cch = 3.0

| Змінна | Клас | Обґрунтування | Вага |
|---|:-:|---|---:|
| `itemId` | T | Передається в `call.call`, `success`, `failed` | 0.5 |
| `ctx` | T | Передається в `success`, `failed` | 0.5 |
| `e` | T | Передається в `failed` | 0.5 |
| `cfg.requestTimeoutMs` | T | Передається в `call.call` | 0.5 |
| `call` | P | Колаборатор | 1 |

**`failed(itemId, ctx, e)`**: Cch = 6.0

| Змінна | Клас | Обґрунтування | Вага |
|---|:-:|---|---:|
| `itemId` | P | Читається в логу, передається далі: P > T | 1 |
| `ctx` | **C** | `ctx.canRetry()` керує вибором гілки | 3 |
| `e` | P | `e.getMessage()` | 1 |
| `log` | P | Колаборатор | 1 |

**`retry(itemId, ctx)`**: Cch = 2.5

| Змінна | Клас | Обґрунтування | Вага |
|---|:-:|---|---:|
| `itemId` | T | Передається в `attempt` | 0.5 |
| `ctx` | P | Читається (`backoffMs()`, `next()`) | 1 |
| `sleeper` | P | Колаборатор | 1 |

**`success(itemId, price, ctx)`**: Cch = 5.5

| Змінна | Клас | Обґрунтування | Вага |
|---|:-:|---|---:|
| `itemId` | P | Читається в логу | 1 |
| `price` | T | Передається в `CallResult` | 0.5 |
| `ctx` | **C** | `ctx.isRetry()` в умові `if` | 3 |
| `log` | P | Колаборатор | 1 |

**`fallback(itemId, ctx, lastError)`**: Cch = 4.5

| Змінна | Клас | Обґрунтування | Вага |
|---|:-:|---|---:|
| `itemId` | P | Читається в логу | 1 |
| `ctx` | P | Читається (`number()`, `elapsedMs()`) | 1 |
| `lastError` | P | Лише читається (параметр `final`) | 1 |
| `cfg.fallbackPrice` | T | Передається в `CallResult` | 0.5 |
| `log` | P | Колаборатор | 1 |

### 2.5 Підсумок

| Показник | ДО | ПІСЛЯ |
|---|---:|---:|
| Змінних типу M | 2 | **0** |
| Змінних типу C (у найскладнішому блоці) | 2 | 1 |
| Cch найскладнішого блоку | **20.0** | **6.0** (`failed`) |
| Усі блоки Cch ≤ 6 | ні | **так** |
| McCabe, max по методах класу | 5 | 2 |

```
Cch_before = 20.0  →  Cch_after(max) = 6.0   (падіння у 20 / 6 ≈ 3.3 раза, умова ≥ 2 і ≤ 6 виконана)
```

**Чесне застереження (знати на захисті).** Сума Cch по всіх 6 блоках = 1 + 3 + 6 + 2.5 + 5.5 + 4.5 = 22.0, тобто не менша за 20: загальні ідентифікатори (`itemId`, `ctx`, `log`) повторюються в кожному блоці. Метрика Чепіна оцінює **модуль**, а не суму модулів: головний виграш у тому, що жоден блок більше не містить мутабельного стану і жоден не перевищує 6. Другий компроміс: рекурсія має глибину `maxRetries + 1`, тому при нереально великих `maxRetries` (тисячі) варто повернути цикл; для типових 3 це безпечно.

**[СКРІН: зелений прогін усіх тестів (`BackoffMutationTest`, `RetryValueObjectsTest`, `ResilientPricingClientTest`)]**
**[СКРІН: вивід ComplexityMeter ПІСЛЯ]**

Коміт: `refactor(audit): reduce Chapin complexity using immutable Value Objects, PASS`

**[СКРІН: `git log --oneline --graph` з комітами FAIL → PASS]**

---

## Завдання 3. CI/CD та Hotspots

### 3.1 Код автоматизації

Файл `.github/workflows/hotspots.yml` (повний лістинг у репозиторії). На кожен push / PR у контейнері `eclipse-temurin:21-jdk`: ставиться git, клонується повна історія (`fetch-depth: 0`), збирається CLI-сканер, весь `src/main/java` експортується в `complexity.csv`, збирається `churn.txt`, будується матриця `hotspots.md/csv`, усе вивантажується як артефакт `hotspots-report`.

**[СКРІН: успішний Job у GitHub Actions + артефакт `hotspots-report`]**

### 3.2 Лог метрик процесу (Churn Rate)

```powershell
git log --since="90 days ago" --format=format: --name-only | Where-Object { $_ } | Group-Object | Sort-Object Count -Descending | ForEach-Object { "{0,5} {1}" -f $_.Count, $_.Name }
```
(еквівалент `git log --format=format: --name-only | egrep -v '^$' | sort | uniq -c | sort -nr`; на Windows PowerShell немає `grep`/`sort -nr`).

**[ВСТАВИТИ: вивід команди]**

### 3.3 Синергетична матриця Hotspots

Складність файлу = **max CC серед його методів** (поріг `CC > 10`); висока мінливість = кількість комітів за 90 днів **> медіани** по проєкту (поріг друкується в `hotspots.md`).

**[ВСТАВИТИ: вміст `docs/lab7/hotspots.md`. Мінімум 5 файлів]**

Управлінські висновки для наступного спринту (заповнити за вашою таблицею):

- **High Risk**: рефакторити першими (виносити відповідальності, покривати тестами).
- **Sleeping Danger**: не чіпати, доки немає Change Request; поставити quality gate (`ComplexityMeter --fail-over`).
- **Active Development**: ідеальний стан; приклад: `ResilientPricingClient` після ЛР№7 (max CC = 2 при високій мінливості).
- **Stable Code**: без дій.

### 3.4 Лог валідації ШІ-Техліда

**[ВСТАВИТИ: експорт цього діалогу з Claude як лог взаємодії з ШІ згідно з Регламентом]**

---

## Шпаргалка до захисту (штраф -2 за незнання)

- **Ваги:** C=3 (керує потоком, будь-яка зміна змінює шлях виконання), M=2 (стан змінюється, треба стежити за всіма записами), P=1 (пасивне читання), T=0.5 (просто проходить крізь метод).
- **Мутабельність vs імутабельність:** мутабельний об'єкт можна змінити після створення (`delayMs *= 2`), імутабельний ні: «зміна» = створення нового (`ctx.next()`). У FAIL-демо саме тому один перезапис `delayMs` зруйнував backoff.
- **Чому Value Object:** групує пов'язані значення, порівнюється за значенням, незмінний, містить власну логіку (`RetryPolicy.delayAfter`).
- **Чому McCabe «сліпий»:** CC = 5 у методі з 16 змінними та 3 мутабельними: топологія проста, дані заплутані.
