# Восстановление профиля IntelliJ IDEA 2024.3 после сброса настроек

**Дата инцидента:** 19.06.2026  
**IDE:** IntelliJ IDEA Ultimate 2024.3 (`IU-243.21565.193`)  
**Путь к IDE:** `C:\Program Files\JetBrains\IntelliJIdea2024.3\bin\idea64.exe`  
**Пользователь:** `sky`

---

## Краткое резюме

IntelliJ IDEA **не «сломала» профиль сама по себе** — при запуске 19.06.2026 в 10:02 IDE выполнила **принудительную миграцию конфигурации с опцией «Start with clean config»** (начать с чистого профиля). Старые настройки перед этим были **автоматически сохранены в бэкап**. Профиль успешно восстановлен из этого бэкапа.

---

## Симптомы

- IDE открылась «как после первой установки»: мастер первоначальной настройки, тема и шрифты по умолчанию
- Пропали пользовательские плагины (Kilo Code, DevoxxGenie, PlantUML и др.)
- Сократился список недавних проектов
- В `%APPDATA%\JetBrains\IntelliJIdea2024.3\options` осталось ~25 файлов вместо ~77
- Папка `plugins` в конфиге отсутствовала

---

## Что на самом деле произошло

### Механизм сброса

При старте IDE (ещё до отображения основного интерфейса) сработал встроенный пайплайн **Config Import / Migration**. В логе зафиксирована цепочка:

```
2026-06-19 10:02:31  Importing configs to C:\Users\sky\AppData\Roaming\JetBrains\IntelliJIdea2024.3
2026-06-19 10:02:31  Custom migration option: Start with clean config
2026-06-19 10:02:32  Backup config from ...\IntelliJIdea2024.3 to ...\Temp\IntelliJIdea2024.3-backup-...
2026-06-19 10:04:18  No configs imported, starting with clean configs at ...\IntelliJIdea2024.3
2026-06-19 10:04:18  Move backup ... to ...\IntelliJIdea2024.3-backup\2026-06-19-10-04
2026-06-19 10:05:28  Will enter initial app wizard flow.
```

**Последовательность событий:**

1. IDE получила команду начать миграцию с опцией **«чистый конфиг»**
2. Текущий профиль (~1.39 GB) скопирован во временную папку `%TEMP%`
3. На месте старого профиля создан новый пустой
4. Бэкап перенесён в постоянное хранилище:  
   `%APPDATA%\JetBrains\IntelliJIdea2024.3-backup\2026-06-19-10-04`
5. Запущен мастер первоначальной настройки (onboarding wizard)

### Что это **не** было

| Гипотеза | Почему маловероятно |
|----------|---------------------|
| Просто некорректное завершение IDE | Предыдущая сессия (18.06, 23:05) завершилась с ошибками WSL/ijent, но это влияет на кэши (`Local`), а не вызывает «clean config» |
| Повреждение файлов настроек | При повреждении IDE обычно пытается восстановить конфиг, а не создавать новый с нуля |
| Удаление папки пользователем | Бэкап IDE содержит полный профиль с датами файлов за месяцы использования |

### Наиболее вероятные причины опции «Start with clean config»

Точный триггер из лога **не назван явно** (`Custom ConfigImportSettings instance: null`), но опция задаётся **до UI**, на этапе `AppStarter`. Типичные источники:

1. **Выбор в мастере импорта** — «Не импортировать / начать заново» (если мастер был показан ранее или при обновлении)
2. **Переустановка / Repair установки** JetBrains IDE — installer может запустить миграцию конфигурации
3. **Внешний `-javaagent`** — в данной установке используется `ja-netfilter` через  
   `C:\Program Files\JetBrains\license\vmoptions\idea.vmoptions`  
   Агент подключается при каждом запуске и **не удалялся** при восстановлении профиля
4. **JetBrains Toolbox** — смена канала обновлений или импорт настроек из другой версии

> **Важно:** `-javaagent` с `ja-netfilter.jar` — обязательная часть запуска в этой среде. Файл `idea.vmoptions` не изменялся при восстановлении.

### Почему встроенный мастер импорта не помог автоматически

После сброса IDE искала профили для импорта в `%APPDATA%\JetBrains\`, но **пропустила бэкап**:

```
Found IntelliJIdea2024.3-backup under ...\JetBrains
IntelliJIdea2024.3-backup doesn't contain options directory, skipping it
```

Причина: бэкап лежит **на уровень глубже** — в подпапке `2026-06-19-10-04\options`, а не в `IntelliJIdea2024.3-backup\options`. Мастер ожидает `options` сразу в корне каталога IDE-профиля.

---

## Диагностика (пошагово)

### 1. Проверка каталогов конфигурации JetBrains

На Windows у IntelliJ два основных каталога:

| Назначение | Путь |
|------------|------|
| **Config** (настройки, плагины, keymaps) | `%APPDATA%\JetBrains\IntelliJIdea2024.3` |
| **System** (кэши, индексы, логи) | `%LOCALAPPDATA%\JetBrains\IntelliJIdea2024.3` |

Команда PowerShell:

```powershell
Get-ChildItem "$env:APPDATA\JetBrains" | Format-Table Name, LastWriteTime
Get-ChildItem "$env:LOCALAPPDATA\JetBrains" | Format-Table Name, LastWriteTime
```

**Найдено:**

- `IntelliJIdea2024.3` — текущий профиль (свежий, ~25 options-файлов)
- `IntelliJIdea2024.3-backup` — автоматический бэкап от 19.06.2026 10:04

### 2. Сравнение «до» и «после»

```powershell
$current = "$env:APPDATA\JetBrains\IntelliJIdea2024.3"
$backup  = "$env:APPDATA\JetBrains\IntelliJIdea2024.3-backup\2026-06-19-10-04"

(Get-ChildItem "$current\options" -File).Count   # было: 25
(Get-ChildItem "$backup\options" -File).Count    # было: 77
Test-Path "$current\plugins"                     # было: False
(Get-ChildItem "$backup\plugins" -Directory).Count  # было: 22
```

Вывод: текущий профиль — минимальный, бэкап — полный.

### 3. Анализ лога IDE

Файл: `%LOCALAPPDATA%\JetBrains\IntelliJIdea2024.3\log\idea.log`

```powershell
Select-String -Path "$env:LOCALAPPDATA\JetBrains\IntelliJIdea2024.3\log\idea.log" `
  -Pattern "clean config|Backup config|ConfigBackup|migration" | Select-Object -First 20
```

Ключевые строки — см. раздел «Что на самом деле произошло».

### 4. Проверка vmoptions и javaagent

```powershell
Get-Content "C:\Program Files\JetBrains\license\vmoptions\idea.vmoptions"
Test-Path "C:\Program Files\JetBrains\license\ja-netfilter.jar"
```

Подтверждено наличие строки:

```
-javaagent:C:\Program Files\JetBrains\license\ja-netfilter.jar=jetbrains
```

### 5. Проверка, что IDE не запущена

Перед восстановлением:

```powershell
Get-Process -Name "idea64" -ErrorAction SilentlyContinue
```

---

## Решение

### Предусловия

- IntelliJ IDEA **полностью закрыта** (нет процесса `idea64.exe`)
- Бэкап существует:  
  `%APPDATA%\JetBrains\IntelliJIdea2024.3-backup\2026-06-19-10-04`

### Шаг 1. Архивировать «сброшенный» профиль

```powershell
$timestamp = Get-Date -Format "yyyy-MM-dd-HH-mm"
$current = "$env:APPDATA\JetBrains\IntelliJIdea2024.3"
$freshArchive = "$env:APPDATA\JetBrains\IntelliJIdea2024.3-fresh-$timestamp"

Move-Item -Path $current -Destination $freshArchive
```

Результат: `IntelliJIdea2024.3-fresh-2026-06-19-10-10` — сохранён на случай отката.

### Шаг 2. Восстановить профиль из бэкапа

```powershell
$backup = "$env:APPDATA\JetBrains\IntelliJIdea2024.3-backup\2026-06-19-10-04"
$current = "$env:APPDATA\JetBrains\IntelliJIdea2024.3"

Copy-Item -Path $backup -Destination $current -Recurse -Force
```

> Первый запуск копирования был прерван — восстановилось только 11 из 22 плагинов.

### Шаг 3. Досинхронизировать недостающие файлы

```powershell
robocopy $backup $current /E /XO /R:2 /W:2
```

Флаги:
- `/E` — все подкаталоги, включая пустые
- `/XO` — не перезаписывать более новые файлы в текущем профиле
- `/R:2 /W:2` — ограничение повторов при блокировке

### Итог восстановления

| Компонент | После восстановления |
|-----------|----------------------|
| `options/*.xml` | 77 файлов |
| `plugins/` | 22 плагина |
| `keymaps/`, `codestyles/`, `colors/` | восстановлены |
| `recentProjects.xml` | список проектов на месте |
| `ja-netfilter` в vmoptions | **не изменялся** |

### Шаг 4. Запуск IDE

```text
"C:\Program Files\JetBrains\IntelliJIdea2024.3\bin\idea64.exe"
```

При появлении мастера импорта — **не выбирать** «Start with clean config» / «Don't import».

---

## Структура бэкапа (что сохранила IDE)

```
IntelliJIdea2024.3-backup\2026-06-19-10-04\
├── options\          # 77 XML-файлов настроек
├── plugins\          # 22 пользовательских плагина
├── keymaps\
├── codestyles\
├── colors\
├── workspace\        # состояние окон по проектам
├── tasks\            # задачи TODO по проектам
├── jdbc-drivers\
├── scratches\
├── disabled_plugins.txt
├── c.kdbx / c.pwd    # хранилище паролей
└── ...
```

Размер бэкапа: **~1.39 GB**.

---

## Профилактика

### 1. Не удалять каталог бэкапа

Оставить минимум на несколько недель:

```
%APPDATA%\JetBrains\IntelliJIdea2024.3-backup\
```

### 2. Включить JetBrains Settings Sync (опционально)

`Settings → Backup and Sync` — синхронизация настроек через JetBrains Account.

### 3. Периодический ручной бэкап

```powershell
$date = Get-Date -Format "yyyy-MM-dd"
robocopy "$env:APPDATA\JetBrains\IntelliJIdea2024.3" `
         "$env:USERPROFILE\Backups\IntelliJIdea2024.3-$date" /E /R:1 /W:1
```

### 4. При мастере импорта

Если IDE снова предложит импорт/миграцию:

- ✅ **Import settings** / импорт из предыдущей версии
- ❌ **Start with clean config** / «начать с нуля»

### 5. Быстрое восстановление вручную (шпаргалка)

Если сброс повторится и есть свежий бэкап в `IntelliJIdea2024.3-backup\<дата-время>\`:

```powershell
# 1. Закрыть IDEA
# 2. Переименовать текущий профиль
Rename-Item "$env:APPDATA\JetBrains\IntelliJIdea2024.3" `
            "$env:APPDATA\JetBrains\IntelliJIdea2024.3-broken"

# 3. Скопировать бэкап
Copy-Item "$env:APPDATA\JetBrains\IntelliJIdea2024.3-backup\2026-06-19-10-04" `
          "$env:APPDATA\JetBrains\IntelliJIdea2024.3" -Recurse
```

> Замените `2026-06-19-10-04` на актуальную подпапку внутри `-backup`.

---

## Справочник: пути и файлы

| Объект | Путь |
|--------|------|
| Исполняемый файл | `C:\Program Files\JetBrains\IntelliJIdea2024.3\bin\idea64.exe` |
| VM options (с javaagent) | `C:\Program Files\JetBrains\license\vmoptions\idea.vmoptions` |
| JA-Netfilter agent | `C:\Program Files\JetBrains\license\ja-netfilter.jar` |
| Config (Roaming) | `C:\Users\sky\AppData\Roaming\JetBrains\IntelliJIdea2024.3` |
| System (Local) | `C:\Users\sky\AppData\Local\JetBrains\IntelliJIdea2024.3` |
| Лог | `C:\Users\sky\AppData\Local\JetBrains\IntelliJIdea2024.3\log\idea.log` |
| Автобэкап | `C:\Users\sky\AppData\Roaming\JetBrains\IntelliJIdea2024.3-backup\2026-06-19-10-04` |
| Архив сброшенного профиля | `C:\Users\sky\AppData\Roaming\JetBrains\IntelliJIdea2024.3-fresh-2026-06-19-10-10` |

---

## Хронология инцидента

| Время | Событие |
|-------|---------|
| 18.06.2026 23:05 | Предыдущая сессия IDE завершилась с ошибками WSL/ijent (некорректное завершение кэшей) |
| 19.06.2026 10:02 | Запуск IDE → `Start with clean config` |
| 19.06.2026 10:02–10:04 | Создание бэкапа старого профиля (~1.39 GB) |
| 19.06.2026 10:04 | Активирован чистый профиль, бэкап сохранён в `-backup\2026-06-19-10-04` |
| 19.06.2026 10:05 | Мастер onboarding (импорт из бэкапа автоматически не сработал) |
| 19.06.2026 ~10:10 | Ручное восстановление профиля из бэкапа |
| 19.06.2026 ~10:13 | Досинхронизация плагинов (22/22), профиль полностью восстановлен |

---

*Документ создан по результатам диагностики и восстановления профиля IntelliJ IDEA 2024.3, 19.06.2026.*
