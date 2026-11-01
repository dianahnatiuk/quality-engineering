# Reliability Report (автогенерація 2026-11-02 01:13:38)

Файл: `src/main/java/lab2/resilience/ResilientPricingClient.java` · локальний java: `scripts/lab5/reliability.sh: line 51: java: command not found`

| Раннер | Варіант | exit | байт | SHA-256 (8) | LOC фіз. | LOC непорожніх | javac-помилки у логу | getPrice: Lines/Cyclo/Cogn | Клас: methods / max Cogn / max Cyclo / Σ Cogn / Σ Cyclo | = еталон? |
|---|---|---|---|---|---|---|---|---|---|---|
| local | lf | 127 | 4609 | 65ce5d35 | 109 | 97 | 0 | **ПОМИЛКА** | див. build/lab5/reliability/raw_local_lf.txt | ❌ |
| local | crlf | 127 | 4719 | 1d4c8ca7 | 109 | 97 | 0 | **ПОМИЛКА** | див. build/lab5/reliability/raw_local_crlf.txt | ❌ |
| local | bom | 127 | 4612 | 61f3bd76 | 109 | 97 | 0 | **ПОМИЛКА** | див. build/lab5/reliability/raw_local_bom.txt | ❌ |
| local | cp1251 | 127 | 4153 | fe6bb468 | 109 | 97 | 0 | **ПОМИЛКА** | див. build/lab5/reliability/raw_local_cp1251.txt | ❌ |
| jdk17 | lf | 0 | 4609 | 65ce5d35 | 109 | 97 | 0 | 52 / 7 / 11 | 4 / 11 / 7 / 13 / 11 | ✅ так |
| jdk17 | crlf | 0 | 4719 | 1d4c8ca7 | 109 | 97 | 0 | 52 / 7 / 11 | 4 / 11 / 7 / 13 / 11 | ✅ так |
| jdk17 | bom | 0 | 4612 | 61f3bd76 | 109 | 97 | 2 | 52 / 7 / 11 | 4 / 11 / 7 / 13 / 11 | ✅ так |
| jdk17 | cp1251 | 0 | 4153 | fe6bb468 | 109 | 97 | 100 | 52 / 7 / 11 | 4 / 11 / 7 / 13 / 11 | ✅ так |
| jdk21 | lf | 0 | 4609 | 65ce5d35 | 109 | 97 | 0 | 52 / 7 / 11 | 4 / 11 / 7 / 13 / 11 | ✅ так |
| jdk21 | crlf | 0 | 4719 | 1d4c8ca7 | 109 | 97 | 0 | 52 / 7 / 11 | 4 / 11 / 7 / 13 / 11 | ✅ так |
| jdk21 | bom | 0 | 4612 | 61f3bd76 | 109 | 97 | 4 | 52 / 7 / 11 | 4 / 11 / 7 / 13 / 11 | ✅ так |
| jdk21 | cp1251 | 0 | 4153 | fe6bb468 | 109 | 97 | 100 | 52 / 7 / 11 | 4 / 11 / 7 / 13 / 11 | ✅ так |
| jdk23 | lf | 0 | 4609 | 65ce5d35 | 109 | 97 | 0 | 52 / 7 / 11 | 4 / 11 / 7 / 13 / 11 | ✅ так |
| jdk23 | crlf | 0 | 4719 | 1d4c8ca7 | 109 | 97 | 0 | 52 / 7 / 11 | 4 / 11 / 7 / 13 / 11 | ✅ так |
| jdk23 | bom | 0 | 4612 | 61f3bd76 | 109 | 97 | 4 | 52 / 7 / 11 | 4 / 11 / 7 / 13 / 11 | ✅ так |
| jdk23 | cp1251 | 0 | 4153 | fe6bb468 | 109 | 97 | 100 | 52 / 7 / 11 | 4 / 11 / 7 / 13 / 11 | ✅ так |
