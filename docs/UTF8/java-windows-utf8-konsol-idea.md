# Кириллица в консоли IDEA: ромбы вместо русских букв

Памятка для Gradle + IntelliJ IDEA на русской Windows. Симптом: `System.out.println("видит")` печатает `♦♦♦♦` или кракозябры. Исходник при этом в редакторе выглядит нормально.

Готовый пример уже стоит в модуле `reactive-study` (`build.gradle`, `gradle.properties`, `.run/ContextDemo.run.xml`).

---

## 1. В чём ошибка

Это не баг `Context` и не «битый» `.java`.

Файл обычно сохранён в **UTF-8**. Java 18+ тоже считает **UTF-8** кодировкой по умолчанию для API. Но **консоль** (`System.out` / `System.err`) на Windows берёт **системную** кодировку: на русской Windows это `Cp1251` (`windows-1251`).

Консоль Gradle / IDEA читает байты как UTF-8. Байты `видит` из Cp1251 для UTF-8 невалидны → вместо букв ромбы.

Проверка (должно быть так):

```text
file.encoding    = UTF-8
native.encoding  = Cp1251
stdout.encoding  = Cp1251
stderr.encoding  = Cp1251
```

Команда:

```bash

java -XshowSettings:properties -version 2>&1 | findstr encoding
```

Источник: https://openjdk.org/jeps/400

**Цитата:**
> Specify UTF-8 as the default charset of the standard Java APIs. … Standardize on UTF-8 throughout the standard Java APIs, except for console I/O.

**Перевод:**
> Зафиксировать UTF-8 как кодировку по умолчанию стандартных Java API. … Стандартизировать UTF-8 во всех стандартных Java API, **кроме ввода-вывода консоли**.

Источник: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html#getProperties()

**Цитата:**
> `stdout.encoding` — Character encoding name for `System.out`. The Java runtime can be started with the system property set to `UTF-8` …
>
> The encoding used in the conversion from characters to bytes is equivalent to `Console.charset()` if the `Console` exists, `stdout.encoding` otherwise.

**Перевод:**
> `stdout.encoding` — имя кодировки для `System.out`. Среда выполнения Java может быть запущена со свойством `UTF-8` …
>
> Кодировка при преобразовании символов в байты совпадает с `Console.charset()`, если консоль есть; иначе используется `stdout.encoding`.

---

## 2. Что сделать в каждом таком проекте

Нужно, чтобы **четыре места** говорили на UTF-8: исходники, компилятор, JVM при запуске `main`/`test`, консоль IDEA.

### 2.1. Gradle: компиляция

Если `encoding` не задан, Gradle берёт кодировку платформы.

Источник: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.compile.CompileOptions.html

**Цитата:**
> The character encoding to be used when reading source files. Defaults to `null`, in which case the platform default encoding will be used.

**Перевод:**
> Кодировка при чтении исходников. По умолчанию `null` — тогда используется кодировка платформы.

В `build.gradle`:

```gradle

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}
```

### 2.2. Gradle: запуск `main` и тестов

В `build.gradle`:

```gradle

tasks.withType(JavaExec).configureEach {
    jvmArgs '-Dfile.encoding=UTF-8',
            '-Dstdout.encoding=UTF-8',
            '-Dstderr.encoding=UTF-8'
}

tasks.named('test') {
    useJUnitPlatform()
    jvmArgs '-Dfile.encoding=UTF-8',
            '-Dstdout.encoding=UTF-8',
            '-Dstderr.encoding=UTF-8'
}
```

`file.encoding=UTF-8` задаёт charset API. Для консоли обязательно ещё `stdout.encoding` и `stderr.encoding`: без них `System.out` останется в `Cp1251`.

### 2.3. Gradle daemon

Файл `gradle.properties` в корне модуля:

```properties

org.gradle.jvmargs=-Dfile.encoding=UTF-8
```

### 2.4. IntelliJ IDEA: кодировка проекта

Источник: https://www.jetbrains.com/help/idea/encoding.html

**Цитата:**
> Source code files are usually encoded in UTF-8. This is the recommended encoding unless you have other requirements.
>
> By default, IntelliJ IDEA uses the system encoding to view console output.

**Перевод:**
> Исходники обычно в UTF-8. Это рекомендуемая кодировка, если нет других требований.
>
> По умолчанию IntelliJ IDEA показывает вывод консоли в системной кодировке.

**Settings → Editor → File Encodings** (`Ctrl+Alt+S`):

- Global Encoding: `UTF-8`
- Project Encoding: `UTF-8`
- Default encoding for properties files: `UTF-8`

Либо в `.idea/encodings.xml`:

```xml
<component name="Encoding" defaultCharsetForPropertiesFiles="UTF-8">
  <file url="PROJECT" charset="UTF-8" />
</component>
```

**Settings → Editor → General → Console → Default Encoding:** `UTF-8`.

### 2.5. Run Configuration в IDEA

Если запускаешь `main` как **Application** (не через Gradle-задачу `*.main()`):

**Run → Edit Configurations → VM options:**

```text
-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8
```

Конфиг можно положить в `.run/ИмяКласса.run.xml`, чтобы не настраивать каждый раз.

После правок: **Build → Rebuild Project**, затем запуск заново.

---

## 3. Как проверить, что починилось

Временный `main` или уже существующий класс:

```java

System.out.println("Operator3 видит: K3=value3");
```

Ожидание в консоли IDEA:

```text
Operator3 видит: K3=value3
```

Повторная проверка свойств JVM уже **с флагами** — `stdout.encoding` должен стать `UTF-8`:

```bash

java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -XshowSettings:properties -version 2>&1 | findstr encoding
```

---

## 4. Чеклист на новый модуль

| Куда | Что |
|---|---|
| `build.gradle` | `JavaCompile.options.encoding = 'UTF-8'` |
| `build.gradle` | `JavaExec` / `test`: `stdout.encoding` + `stderr.encoding` + `file.encoding` = UTF-8 |
| `gradle.properties` | `org.gradle.jvmargs=-Dfile.encoding=UTF-8` |
| IDEA File Encodings | Project = UTF-8 |
| IDEA Console | Default Encoding = UTF-8 |
| Run Configuration | три `-D…encoding=UTF-8` |

Не чини это заменой русских строк на английские в `println`. Кодировку нужно выровнять, а не прятать симптом.
