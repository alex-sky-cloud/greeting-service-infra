> **Источник / source:** [https://projectreactor.io/docs/core/release/reference/](https://projectreactor.io/docs/core/release/reference/)  
> Официальный *Reactor Core Reference Guide* версии **3.8.6** (авторы: Stephane Maldini [@smaldini], Simon Baslé [@simonbasle]; Antora HTML, generation 3.8).  
> Это полный перевод прозы на русский плюс пояснения операторов. Код скопирован дословно. Технические термины (Reactor, Flux, Mono, Publisher, Subscriber, Subscription, backpressure, Scheduler, sink, operator, Context, Hooks, StepVerifier, virtual time и т.д.) оставлены на английском.

# Reactor Core Reference Guide (Справочное руководство Reactor Core)

Stephane Maldini, Simon Baslé

3.8.6

## Table of contents (Содержание)

- [About the Documentation (О документации)](#about-the-documentation-о-документации)
  - [1. Latest Version & Copyright Notice](#1-latest-version--copyright-notice)
  - [2. Contributing to the Documentation (Вклад в документацию)](#2-contributing-to-the-documentation-вклад-в-документацию)
  - [3. Getting Help (Помощь)](#3-getting-help-помощь)
  - [4. Where to Go from Here (Куда идти дальше)](#4-where-to-go-from-here-куда-идти-дальше)
- [Getting Started (Начало работы)](#getting-started-начало-работы)
  - [1. Introducing Reactor](#1-introducing-reactor)
  - [2. Prerequisites (Требования)](#2-prerequisites-требования)
  - [3. Understanding the BOM and versioning scheme](#3-understanding-the-bom-and-versioning-scheme)
  - [4. Getting Reactor](#4-getting-reactor)
  - [5. Support and policies](#5-support-and-policies)
- [Introduction to Reactive Programming (Введение в reactive programming)](#introduction-to-reactive-programming-введение-в-reactive-programming)
  - [1. Blocking Can Be Wasteful](#1-blocking-can-be-wasteful)
  - [2. Asynchronicity to the Rescue?](#2-asynchronicity-to-the-rescue)
  - [3. From Imperative to Reactive Programming](#3-from-imperative-to-reactive-programming)
- [Reactor Core Features](#reactor-core-features)
  - [Flux, an Asynchronous Sequence of 0-N Items](#flux-an-asynchronous-sequence-of-0-n-items)
  - [Mono, an Asynchronous 0-1 Result](#mono-an-asynchronous-0-1-result)
  - [Simple Ways to Create a Flux or Mono and Subscribe to It](#simple-ways-to-create-a-flux-or-mono-and-subscribe-to-it)
  - [Programmatically creating a sequence](#programmatically-creating-a-sequence-программное-создание-последовательности)
  - [Threading and Schedulers](#threading-and-schedulers-потоки-и-schedulers)
  - [Handling Errors](#handling-errors-обработка-ошибок)
  - [Processors and Sinks](#processors-and-sinks)
- [Testing](#testing-тестирование)
- [Debugging](#debugging-отладка)
- [Metrics](#metrics)
- [Kotlin support](#kotlin-support)
- [Advanced Features and Concepts](#advanced-features-and-concepts-продвинутые-возможности)
  - [Hot vs Cold](#hot-vs-cold)
  - [Broadcasting with ConnectableFlux](#broadcasting-with-connectableflux)
  - [Three Sorts of Batching](#three-sorts-of-batching)
  - [Parallelizing with ParallelFlux](#parallelizing-with-parallelflux)
  - [Mutualizing Operator Usage](#mutualizing-operator-usage)
  - [Hooks](#hooks)
  - [Context](#context)
  - [Context Propagation](#context-propagation)
  - [Null Safety](#null-safety)
  - [Cleanup](#cleanup)
  - [Scheduler Factory](#scheduler-factory)
- [FAQ](#faq)
- [Appendix A: How to Read Marble Diagrams](#appendix-a-how-to-read-marble-diagrams)
- [Appendix B: Which operator do I need?](#appendix-b-which-operator-do-i-need)
- [Appendix C: Reactor Extra](#appendix-c-reactor-extra)

# About the Documentation (О документации)

Этот раздел даёт краткий обзор справочной документации Reactor. Руководство не обязательно читать линейно: каждая часть самостоятельна, хотя часто ссылается на другие.

## 1. Latest Version & Copyright Notice

Справочное руководство Reactor доступно как HTML-документы. Последняя копия: [projectreactor.io/docs/core/release/reference/index.html](https://projectreactor.io/docs/core/release/reference/index.html).

Копии этого документа можно делать для собственного использования и для распространения другим, при условии что вы не взимаете плату за такие копии и каждая копия содержит это Copyright Notice — как в печатном, так и в электронном виде.

## 2. Contributing to the Documentation (Вклад в документацию)

Руководство написано на [Asciidoc](https://asciidoctor.org/docs/asciidoc-writers-guide/) с использованием [Antora](https://docs.antora.org/antora/latest/). Исходники: [github.com/reactor/reactor-core/tree/main/docs/](https://github.com/reactor/reactor-core/tree/main/docs/).

Если у вас есть улучшение или предложение, авторы будут рады pull request.

Рекомендуется локально клонировать репозиторий, чтобы сгенерировать документацию gradle-задачей `asciidoctor` и проверить рендер. Некоторые разделы включают файлы, поэтому GitHub-рендер не всегда полный.

Ссылку `Edit this Page` в правой панели можно использовать для правки текущей страницы напрямую в GitHub (только HTML5-версия руководства).

## 3. Getting Help (Помощь)

С Reactor можно получить помощь несколькими способами:

- Сообщество в [Gitter](https://gitter.im/reactor/reactor).
- Вопрос на stackoverflow.com с тегом [`project-reactor`](https://stackoverflow.com/tags/project-reactor).
- Баги в GitHub issues: [reactor-core](https://github.com/reactor/reactor-core/issues) (основные возможности) и [reactor-addons](https://github.com/reactor/reactor-addons/issues) (`reactor-test` и adapters).

Весь Reactor open source, [включая эту документацию](https://github.com/reactor/reactor-core/tree/main/docs). Если нашли проблемы в docs — [включитесь](https://github.com/reactor/.github/blob/main/CONTRIBUTING.md).

## 4. Where to Go from Here (Куда идти дальше)

- [Getting Started](https://projectreactor.io/docs/core/release/reference/gettingStarted.html), если хотите сразу к коду.
- Если вы новичок в reactive programming — начните с [Introduction to Reactive Programming](https://projectreactor.io/docs/core/release/reference/reactiveProgramming.html).
- Если знакомы с концепциями Reactor и ищете нужный operator — [Which operator do I need?](https://projectreactor.io/docs/core/release/reference/apdx-operatorChoice.html).
- Глубже в core features: [Reactor Core Features](https://projectreactor.io/docs/core/release/reference/coreFeatures.html) — типы `Flux`/`Mono`, Scheduler, Handling Errors.
- Unit testing: проект `reactor-test`, раздел [Testing](https://projectreactor.io/docs/core/release/reference/testing.html).
- [Programmatically creating a sequence](https://projectreactor.io/docs/core/release/reference/producing.html) — продвинутое создание reactive sources.
- Продвинутые темы: [Advanced Features and Concepts](https://projectreactor.io/docs/core/release/reference/advancedFeatures.html).

# Getting Started (Начало работы)

Этот раздел помогает начать работу с Reactor.

## 1. Introducing Reactor

Reactor — полностью non-blocking основа reactive programming для JVM с эффективным управлением demand (в форме управления **backpressure**). Он напрямую интегрируется с функциональными API Java 8, в частности `CompletableFuture`, `Stream` и `Duration`. Он предлагает composable асинхронные sequence API — `Flux` (для [N] элементов) и `Mono` (для [0|1] элементов) — и обширно реализует спецификацию [Reactive Streams](https://www.reactive-streams.org/).

Reactor также поддерживает non-blocking inter-process communication проектом `reactor-netty`. Для Microservices Architecture Reactor Netty даёт готовые к backpressure network engines для HTTP (включая Websockets), TCP и UDP. Reactive encoding и decoding полностью поддерживаются.

## 2. Prerequisites (Требования)

Reactor Core работает на `Java 8` и выше.

Транзитивная зависимость: `org.reactivestreams:reactive-streams:1.0.3`.

Android Support:

- Reactor 3 официально не поддерживает и не таргетирует Android (если такая поддержка критична, рассмотрите RxJava 2).
- Однако он должен нормально работать с Android SDK 26 (Android O) и выше.
- Скорее всего будет работать с Android SDK 21 (Android 5.0) и выше при включённом desugaring. См. [developer.android.com/studio/write/java8-support#library-desugaring](https://developer.android.com/studio/write/java8-support#library-desugaring).
- Авторы открыты к оценке изменений, полезных для Android, best-effort; гарантий нет. Каждое решение — case-by-case.

## 3. Understanding the BOM and versioning scheme

Reactor 3 использует модель BOM (Bill of Materials) (с `reactor-core 3.0.4`, release train `Aluminium`). Этот курируемый список группирует артефакты, которые должны хорошо работать вместе, давая релевантные версии несмотря на потенциально расходящиеся схемы версионирования.

Схема версионирования изменилась между 3.3.x и 3.4.x (Dysprosium и Europium).

Артефакты следуют схеме `MAJOR.MINOR.PATCH-QUALIFIER`, а BOM версионируется в духе CalVer: `YYYY.MINOR.PATCH-QUALIFIER`, где:

- `MAJOR` — текущее поколение Reactor; каждое новое поколение может принести фундаментальные изменения структуры проекта (и более значительный migration effort).
- `YYYY` — год первого GA-релиза в данном release cycle (как 3.4.0 для 3.4.x).
- `.MINOR` — 0-based номер, растущий с каждым новым release cycle. Для проектов обычно отражает более широкие изменения; для BOM позволяет различить циклы, если два вышли в одном году.
- `.PATCH` — 0-based номер, растущий с каждым service release.
- `-QUALIFIER` — текстовый qualifier; опускается для GA-релизов.

Первый цикл по этой конвенции — `2020.0.x`, codename `Europium`. Qualifiers (через дефис), по порядку:

- `-M1`..`-M9`: milestones
- `-RC1`..`-RC9`: release candidates
- `-SNAPSHOT`: snapshots
- *без qualifier* для GA

Snapshots стоят выше в этом списке, потому что концептуально это всегда «самый свежий pre-release» данного PATCH. Даже если первый deployed artifact цикла — `-SNAPSHOT`, более свежий snapshot с тем же именем может выйти после milestone или между RC.

Каждому release cycle дают codename (в преемственности прежней схеме), чтобы ссылаться неформально. Codenames — то, что традиционно было MAJOR.MINOR. Они в основном из [Periodic Table of Elements](https://en.wikipedia.org/wiki/Periodic_table#Overview), в возрастающем алфавитном порядке.

До Dysprosium BOM версионировался как release train: codename + qualifier (`Aluminium-RELEASE`, `Bismuth-M1`, `Californium-SR1`, `Dysprosium-RC1`, `Dysprosium-BUILD-SNAPSHOT`).

## 4. Getting Reactor

Самый простой способ использовать Reactor — BOM и нужные зависимости. Когда добавляете такую зависимость, **опускайте version**, чтобы она подтянулась из BOM.

Если нужно форсировать версию конкретного артефакта — укажите её как обычно. Можно и вовсе обойтись без BOM и задать версии артефактов явно.

На момент этой версии (`reactor-core 3.8.6`) последний стабильный BOM в соответствующей линии — `2025.0.6`. Могут быть более новые версии (включая snapshots, milestones и новые release trains); см. [projectreactor.io/docs](https://projectreactor.io/docs).

### 4.1. Maven Installation

Maven нативно поддерживает BOM. Сначала импортируйте BOM в `pom.xml`:

```xml
<dependencyManagement> (1)
    <dependencies>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-bom</artifactId>
            <version>2025.0.6</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**Разбор операторов и ключей**

- `dependencyManagement` — Maven-секция управления версиями (дополнительно к обычным `dependencies`).
- `groupId` `io.projectreactor` — координаты Reactor.
- `artifactId` `reactor-bom` — BOM.
- `version` `2025.0.6` — CalVer BOM, соответствующий `reactor-core` 3.8.6.
- `type` `pom` + `scope` `import` — стандартный Maven BOM import.

Если секция `dependencyManagement` уже есть, добавьте только содержимое.

Далее зависимости без `<version>`:

```xml
<dependencies>
    <dependency>
        <groupId>io.projectreactor</groupId>
        <artifactId>reactor-core</artifactId> (1)
        (2)
    </dependency>
    <dependency>
        <groupId>io.projectreactor</groupId>
        <artifactId>reactor-test</artifactId> (3)
        <scope>test</scope>
    </dependency>
</dependencies>
```

**Разбор операторов и ключей**

- `reactor-core` — core-библиотека (`Flux`, `Mono`, operators).
- Нет тега `version` — версия из BOM.
- `reactor-test` — средства unit-тестирования reactive streams (`StepVerifier` и др.).
- `scope` `test` — только для тестов.

### 4.2. Gradle Installation

До версии 5.0 у Gradle нет нативной поддержки Maven BOM, но можно использовать Spring [gradle-dependency-management](https://github.com/spring-gradle-plugins/dependency-management-plugin).

```groovy
plugins {
    id "io.spring.dependency-management" version "1.0.7.RELEASE" (1)
}
```

**Разбор операторов и ключей**

- `io.spring.dependency-management` — plugin для Maven BOM в Gradle; `1.0.7.RELEASE` — версия на момент написания docs (проверяйте обновления).

```groovy
dependencyManagement {
     imports {
          mavenBom "io.projectreactor:reactor-bom:2025.0.6"
     }
}
```

**Разбор операторов и ключей**

- `dependencyManagement` / `imports` / `mavenBom` — импорт BOM `reactor-bom:2025.0.6`.

```groovy
dependencies {
     implementation 'io.projectreactor:reactor-core' (1)
}
```

**Разбор операторов и ключей**

- `implementation` — Gradle configuration.
- Нет третьей `:`-секции с версией — версия из BOM.

С Gradle 5.0 нативная поддержка BOM:

```groovy
dependencies {
     implementation platform('io.projectreactor:reactor-bom:2025.0.6')
     implementation 'io.projectreactor:reactor-core' (1)
}
```

**Разбор операторов и ключей**

- `platform(...)` — Gradle BOM/platform dependency.
- `reactor-core` без версии.

### 4.3. Milestones and Snapshots

Milestones и release candidates распространяются через `Maven Central`.

Milestone и RC — для тестирования, не для production.

Snapshots — через репозиторий `Spring Snapshots`, не через `Maven Central`:

```xml
<repositories>
	<repository>
		<id>spring-snapshots</id>
		<name>Spring Snapshot Repository</name>
		<url>https://repo.spring.io/snapshot</url>
	</repository>
</repositories>
```

**Разбор операторов и ключей**

- `repositories` / `repository` — Maven remote repo.
- `id` `spring-snapshots`, `url` `https://repo.spring.io/snapshot` — источник `-SNAPSHOT`.

```groovy
repositories {
  maven { url 'https://repo.spring.io/snapshot' }
  mavenCentral()
}
```

**Разбор операторов и ключей**

- `maven { url ... }` — Gradle Maven repository.
- `mavenCentral()` — Maven Central для GA/RC/M.

## 5. Support and policies

Записи ниже зеркалят [github.com/reactor/.github/blob/main/SUPPORT.adoc](https://github.com/reactor/.github/blob/main/SUPPORT.adoc).

### 5.1. Do you have a question?

Сначала поищите на Stack Overflow; обсуждайте при необходимости.

Если неясно, почему что-то не работает, или ищете лучший способ — сначала **Stack Overflow**, при необходимости начните discussion. Используйте теги, которые мониторит команда:

- [`reactor-netty`](https://stackoverflow.com/questions/tagged/reactor-netty)
- [`project-reactor`](https://stackoverflow.com/questions/tagged/project-reactor)

Для real-time обсуждения есть **Gitter**:

- [`reactor`](https://gitter.im/reactor/reactor) — исторически самый активный канал
- [`reactor-core`](https://gitter.im/reactor/reactor-core) — точечные обсуждения внутренностей библиотеки
- [`reactor-netty`](https://gitter.im/reactor/reactor-netty) — вопросы по Netty

См. README каждого проекта. Открывать GitHub issues для вопросов обычно не рекомендуют — лучше два канала выше.

### 5.2. Our policy on **deprecations**

Для версии `A.B.C` гарантируется:

- deprecations, введённые в `A.B.0`, будут удалены **не раньше** `A.(B+1).0`
- deprecations, введённые в `A.B.1+`, будут удалены **не раньше** `A.(B+2).0`
- в javadoc deprecation стараются указать: минимальную версию удаления, замены, версию, в которой метод deprecated

Политика официально с января 2021 для всех модулей в BOM `2020.0` и новее, а также `Dysprosium` после `Dysprosium-SR15`.

Цели удаления — не жёсткое обязательство; deprecated методы **могут жить дольше** этих минимальных GA (агрессивно снимают только самые проблемные).

При этом deprecated код, переживший minimum removal target, может быть удалён в любом последующем релизе (включая patch / service releases) без дополнительного notice. Пользователям всё равно стоит обновлять код как можно раньше.

### 5.3. Support Timeline

Сводка дат поддержки по проектам и BOM: страница [Reactor Support](https://projectreactor.io/support).


# Introduction to Reactive Programming (Введение в reactive programming)

Reactor — реализация парадигмы Reactive Programming, которую можно суммировать так:

> Reactive programming is an asynchronous programming paradigm concerned with data streams and the propagation of change. This means that it becomes possible to express static (e.g. arrays) or dynamic (e.g. event emitters) data streams with ease via the employed programming language(s).

— https://en.wikipedia.org/wiki/Reactive_programming

Первым шагом в этом направлении Microsoft создала библиотеку Reactive Extensions (Rx) в экосистеме .NET. Затем RxJava реализовала reactive programming на JVM. Со временем для Java появилась стандартизация через усилия Reactive Streams — спецификацию, определяющую набор интерфейсов и правил взаимодействия для reactive-библиотек на JVM. Её интерфейсы вошли в Java 9 в класс `Flow`.

Парадигму часто представляют в ОО-языках как расширение паттерна Observer. Основной паттерн reactive streams можно также сравнить с Iterator: во всех этих библиотеках есть двойственность пары `Iterable`-`Iterator`. Важное отличие: Iterator — pull-based, reactive streams — push-based.

Использование iterator — императивный паттерн, даже если доступ к значениям — ответственность `Iterable`. Разработчик сам выбирает, когда взять `next()`. В reactive streams эквивалент пары — `Publisher-Subscriber`. Но именно `Publisher` уведомляет Subscriber о новых значениях *по мере их появления*, и этот push-аспект — ключ к тому, чтобы быть reactive. Операции над проталкиваемыми значениями выражаются декларативно, а не императивно: программист выражает логику вычисления, а не точный control flow.

Помимо push значений, обработка ошибок и completion тоже определены. `Publisher` может пушить новые значения (`onNext`), сигнализировать ошибку (`onError`) или completion (`onComplete`). И ошибка, и completion завершают последовательность:

```java
onNext x 0..N [onError | onComplete]
```

**Разбор операторов и ключей**

- `onNext` — сигнал следующего элемента, 0..N раз.
- `onError` / `onComplete` — взаимоисключающие terminal signals.

Паттерн гибок: нет значения, одно значение или n значений (включая бесконечную последовательность, например тики часов).

Но зачем вообще нужна такая асинхронная reactive-библиотека?

## 1. Blocking Can Be Wasteful

Современные приложения могут достигать огромного числа concurrent users, и хотя железо улучшается, производительность ПО остаётся ключевой.

Есть, грубо, два способа улучшить performance:

- **parallelize** — больше threads и hardware resources.
- **seek more efficiency** — эффективнее использовать текущие ресурсы.

Обычно Java-разработчики пишут blocking-код. Это нормально, пока нет bottleneck. Затем добавляют threads с тем же blocking-кодом. Но такое масштабирование быстро вносит contention и проблемы concurrency.

Хуже того, blocking тратит ресурсы. Как только появляется latency (особенно I/O: БД, сеть), threads (возможно многие) простаивают в ожидании данных.

Поэтому параллелизация — не серебряная пуля. Она нужна для полной мощности железа, но сложна и склонна к wasted resources.

## 2. Asynchronicity to the Rescue?

Второй подход — эффективность — может решить wasted resources. Пиша asynchronous, non-blocking код, вы позволяете execution переключиться на другую активную задачу на тех же ресурсах и позже вернуться, когда асинхронная обработка завершилась.

Как писать асинхронный код на JVM? Java даёт две модели:

- **Callbacks**: асинхронные методы не возвращают значение, а принимают extra `callback` (lambda или anonymous class), вызываемый когда результат готов. Известный пример — иерархия `EventListener` в Swing.
- **Futures**: методы *сразу* возвращают `Future<T>`. Асинхронный процесс вычисляет `T`, но `Future` оборачивает доступ. Значение не сразу доступно; объект можно poll. Например, `ExecutorService` с `Callable<T>` использует `Future`.

Достаточно ли этих техник? Не для каждого use case; у обоих есть ограничения.

Callbacks трудно compose, быстро получается трудночитаемый код («Callback Hell»).

Пример: показать топ-5 favorites пользователя в UI или suggestions, если favorites нет. Три сервиса (IDs, details, suggestions):

```java
userService.getFavorites(userId, new Callback<List<String>>() { (1)
  public void onSuccess(List<String> list) { (2)
    if (list.isEmpty()) { (3)
      suggestionService.getSuggestions(new Callback<List<Favorite>>() {
        public void onSuccess(List<Favorite> list) { (4)
          UiUtils.submitOnUiThread(() -> { (5)
            list.stream()
                .limit(5)
                .forEach(uiList::show); (6)
            });
        }

        public void onError(Throwable error) { (7)
          UiUtils.errorPopup(error);
        }
      });
    } else {
      list.stream() (8)
          .limit(5)
          .forEach(favId -> favoriteService.getDetails(favId, (9)
            new Callback<Favorite>() {
              public void onSuccess(Favorite details) {
                UiUtils.submitOnUiThread(() -> uiList.show(details));
              }

              public void onError(Throwable error) {
                UiUtils.errorPopup(error);
              }
            }
          ));
    }
  }

  public void onError(Throwable error) {
    UiUtils.errorPopup(error);
  }
});
```

**Разбор операторов и ключей**

- `Callback<T>` — `onSuccess` / `onError`.
- `getFavorites` / `getSuggestions` / `getDetails` — callback-based services.
- `UiUtils.submitOnUiThread` — выполнение на UI thread.
- `Stream.limit(5)` / `forEach` — ограничение и показ.

Эквивалент на Reactor:

```java
userService.getFavorites(userId) (1)
           .flatMap(favoriteService::getDetails) (2)
           .switchIfEmpty(suggestionService.getSuggestions()) (3)
           .take(5) (4)
           .publishOn(UiUtils.uiThreadScheduler()) (5)
           .subscribe(uiList::show, UiUtils::errorPopup); (6)
```

**Разбор операторов и ключей**

- `getFavorites` — стартовый `Publisher`/`Flux` ID.
- `flatMap` — асинхронно превращает каждый ID в `Favorite`.
- `switchIfEmpty` — fallback, если поток пуст.
- `take(5)` — не более пяти элементов.
- `publishOn` — последующие сигналы на UI `Scheduler`.
- `subscribe(consumer, errorConsumer)` — запуск цепочки, показ и popup ошибок.

Если IDs нужны менее чем за 800ms, иначе из cache — в callback-коде сложно; в Reactor достаточно `timeout`:

```java
userService.getFavorites(userId)
           .timeout(Duration.ofMillis(800)) (1)
           .onErrorResume(cacheService.cachedFavoritesFor(userId)) (2)
           .flatMap(favoriteService::getDetails) (3)
           .switchIfEmpty(suggestionService.getSuggestions())
           .take(5)
           .publishOn(UiUtils.uiThreadScheduler())
           .subscribe(uiList::show, UiUtils::errorPopup);
```

**Разбор операторов и ключей**

- `timeout(Duration.ofMillis(800))` — если выше по цепи ничего не эмитится 800ms, `onError`.
- `onErrorResume` — fallback на `cacheService`.
- Остальные operators как в предыдущем примере.

`Future` чуть лучше callbacks, но composition всё ещё слабый, несмотря на `CompletableFuture` в Java 8. Оркестрация нескольких `Future` возможна, но не проста. Другие проблемы:

- Легко снова заблокироваться через `get()`.
- Нет lazy computation.
- Нет поддержки нескольких значений и продвинутой обработки ошибок.

Пример: список IDs, для каждого — name и statistic, попарно, асинхронно, через `CompletableFuture`:

```java
CompletableFuture<List<String>> ids = ifhIds(); (1)

CompletableFuture<List<String>> result = ids.thenComposeAsync(l -> { (2)
	Stream<CompletableFuture<String>> zip =
			l.stream().map(i -> { (3)
				CompletableFuture<String> nameTask = ifhName(i); (4)
				CompletableFuture<Integer> statTask = ifhStat(i); (5)

				return nameTask.thenCombineAsync(statTask, (name, stat) -> "Name " + name + " has stats " + stat); (6)
			});
	List<CompletableFuture<String>> combinationList = zip.collect(Collectors.toList()); (7)
	CompletableFuture<String>[] combinationArray = combinationList.toArray(new CompletableFuture[combinationList.size()]);

	CompletableFuture<Void> allDone = CompletableFuture.allOf(combinationArray); (8)
	return allDone.thenApply(v -> combinationList.stream()
			.map(CompletableFuture::join) (9)
			.collect(Collectors.toList()));
});

List<String> results = result.join(); (10)
assertThat(results).contains(
		"Name NameJoe has stats 103",
		"Name NameBart has stats 104",
		"Name NameHenry has stats 105",
		"Name NameNicole has stats 106",
		"Name NameABSLAJNFOAJNFOANFANSF has stats 121");
```

**Разбор операторов и ключей**

- `ifhIds` — `CompletableFuture` списка id.
- `thenComposeAsync` — вложенная async-обработка после списка.
- `ifhName` / `ifhStat` — async name и statistic.
- `thenCombineAsync` — комбинация пары.
- `CompletableFuture.allOf` — completion всех задач.
- `join` — взять результат (здесь не блокирует после `allOf`).

С operators Reactor проще:

```java
Flux<String> ids = ifhrIds(); (1)

Flux<String> combinations =
		ids.flatMap(id -> { (2)
			Mono<String> nameTask = ifhrName(id); (3)
			Mono<Integer> statTask = ifhrStat(id); (4)

			return nameTask.zipWith(statTask, (5)
					(name, stat) -> "Name " + name + " has stats " + stat);
		});

Mono<List<String>> result = combinations.collectList(); (6)

List<String> results = result.block(); (7)
assertThat(results).containsExactly( (8)
		"Name NameJoe has stats 103",
		"Name NameBart has stats 104",
		"Name NameHenry has stats 105",
		"Name NameNicole has stats 106",
		"Name NameABSLAJNFOAJNFOANFANSF has stats 121"
);
```

**Разбор операторов и ключей**

- `ifhrIds` — `Flux<String>` ids.
- `flatMap` — на каждый id две async-задачи.
- `ifhrName` / `ifhrStat` — `Mono` name и stat.
- `zipWith` — асинхронная комбинация двух `Mono`.
- `collectList` — агрегат в `Mono<List<String>>`.
- `block` — в тесте дождаться результата (в production обычно вернули бы `Mono`).
- `assertThat` / `containsExactly` — AssertJ.

Опасности callbacks и `Future` — то, что reactive programming решает парой `Publisher-Subscriber`.

## 3. From Imperative to Reactive Programming

Reactive-библиотеки вроде Reactor стремятся закрыть недостатки «классических» async-подходов на JVM и дополнительно фокусируются на:

- **Composability** и **readability**
- Данные как **flow**, манипулируемый богатым словарём **operators**
- Ничего не происходит, пока вы не **subscribe**
- **Backpressure** — способность consumer сигнализировать producer, что скорость emission слишком высока
- **High level**, но **high value** абстракция, *concurrency-agnostic*

### 3.1. Composability and Readability

«Composability» — оркестрация нескольких async-задач, когда результаты предыдущих кормят последующие. Либо несколько задач в стиле fork-join. Также можно переиспользовать async-задачи как дискретные компоненты более высокой системы.

Оркестрация тесно связана с читаемостью. По мере роста слоёв async-процессов compose и читать код всё труднее. Callback-модель проста, но главный минус — callback из callback внутри ещё одного callback («Callback Hell»). Такой код тяжело перечитывать.

Reactor даёт богатые варианты composition: код отражает организацию абстрактного процесса, всё обычно на одном уровне (минимум nesting).

### 3.2. The Assembly Line Analogy

Данные в reactive-приложении можно мыслить как движущиеся по сборочной линии. Reactor — и конвейер, и рабочие станции. Сырьё льётся из source (исходный `Publisher`) и становится готовым продуктом для consumer (`Subscriber`).

Сырьё проходит трансформации и промежуточные шаги или входит в более крупную линию, агрегирующую куски. Если на станции затор (например boxing занимает непропорционально много времени), она может сигнализировать upstream ограничить поток сырья.

### 3.3. Operators

В Reactor operators — рабочие станции аналогии. Каждый operator добавляет поведение `Publisher` и оборачивает предыдущий `Publisher` в новый экземпляр. Цепочка связана: данные идут от первого `Publisher` вниз, трансформируясь на каждом звене. В конце `Subscriber` завершает процесс. Пока `Subscriber` не подпишется, ничего не происходит.

Понимание того, что operators создают новые экземпляры, помогает избежать ошибки «operator в цепи не применился». См. [FAQ item](https://projectreactor.io/docs/core/release/reference/faq.html#faq.chain).

Reactive Streams вообще не специфицирует operators. Одна из лучших ценностей библиотек вроде Reactor — богатый словарь operators: от простых transform/filter до сложной оркестрации и error handling.

### 3.4. Nothing Happens Until You `subscribe()`

Когда вы пишете цепочку `Publisher`, данные сами по себе в неё не текут. Вы создаёте абстрактное описание асинхронного процесса (это помогает переиспользованию и composition).

Акт **subscribing** связывает `Publisher` с `Subscriber` и запускает поток данных по всей цепи. Внутри это один сигнал `request` от `Subscriber`, распространяющийся upstream до исходного `Publisher`.

### 3.5. Backpressure

Распространение сигналов upstream используется и для **backpressure** — feedback, когда станция медленнее, чем upstream.

Реальный механизм Reactive Streams близок к аналогии: subscriber может работать в *unbounded* режиме и позволить source пушить всё на максимальной скорости, либо через `request` сказать, что готов обработать не более `n` элементов.

Промежуточные operators могут менять request «на лету». Например `buffer`, группирующий по десять: если subscriber запросил один buffer, source может произвести десять элементов. Некоторые operators реализуют **prefetching**, избегая round-trip `request(1)`, если производить элементы заранее не слишком дорого.

Так push-модель становится **push-pull hybrid**: downstream может pull n элементов, если они готовы; если нет — upstream пушит, когда произведёт.

### 3.6. Hot vs Cold

Семейство Rx различает **hot** и **cold** последовательности — в основном по реакции на subscribers:

- **Cold** последовательность начинается заново для каждого `Subscriber`, включая source данных. Если source оборачивает HTTP-вызов, каждый subscribe делает новый HTTP request.
- **Hot** не стартует с нуля для каждого `Subscriber`. Поздние subscribers получают сигналы, эмитированные *после* подписки. Некоторые hot streams кешируют или replay историю полностью или частично. В общем hot sequence может даже эмитить, когда никто не слушает (исключение из правила «nothing happens before you subscribe»).

Подробнее в контексте Reactor: [reactor-specific section](https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html).

# Reactor Core Features

Главный артефакт проекта Reactor — `reactor-core`, reactive-библиотека, сфокусированная на спецификации Reactive Streams и нацеленная на Java 8.

Reactor вводит composable reactive types, которые реализуют `Publisher` и дают богатый словарь operators: `Flux` и `Mono`. `Flux` — reactive sequence 0..N items; `Mono` — single-value-or-empty (0..1) result.

Это различие несёт семантику: грубую cardinality асинхронной обработки. HTTP-запрос даёт один ответ, поэтому `count` особого смысла не имеет. Результат такого вызова как `Mono<HttpResponse>` логичнее, чем `Flux<HttpResponse>`: доступны только operators, релевантные контексту нуля или одного item.

Operators, меняющие максимальную cardinality, переключают тип. Например `count` есть у `Flux`, но возвращает `Mono<Long>`.

## Flux, an Asynchronous Sequence of 0-N Items

`Flux<T>` — стандартный `Publisher<T>`, представляющий асинхронную последовательность 0–N эмитированных items, опционально завершаемую completion или error. Как в Reactive Streams, три типа сигнала соответствуют вызовам `onNext`, `onComplete` и `onError` у downstream `Subscriber`.

При таком широком scope сигналов `Flux` — general-purpose reactive type. Все события, даже terminal, опциональны: нет `onNext`, но есть `onComplete` — *пустая* конечная последовательность; уберите `onComplete` — *бесконечная* пустая последовательность (мало полезна, кроме тестов cancellation). Бесконечные последовательности не обязаны быть пустыми. Например `Flux.interval(Duration)` даёт бесконечный `Flux<Long>` с регулярными тиками часов.

## Mono, an Asynchronous 0-1 Result

`Mono<T>` — специализированный `Publisher<T>`, который эмитит не более одного item через `onNext`, затем завершается `onComplete` (успешный `Mono`, со значением или без), либо эмитит только один `onError` (failed `Mono`).

Большинство реализаций `Mono` сразу вызывают `onComplete` у `Subscriber` после `onNext`. `Mono.never()` — исключение: не эмитит ни одного сигнала (технически не запрещено, вне тестов почти бесполезно). Комбинация `onNext` и `onError` явно запрещена.

`Mono` предлагает только subset operators `Flux`; некоторые (особенно комбинирующие `Mono` с другим `Publisher`) переключаются на `Flux`. Например `Mono#concatWith(Publisher)` возвращает `Flux`, а `Mono#then(Mono)` — другой `Mono`.

`Mono` можно использовать для no-value асинхронных процессов, у которых есть только понятие completion (похоже на `Runnable`). Создайте пустой `Mono<Void>`.

## Simple Ways to Create a Flux or Mono and Subscribe to It

Проще всего начать с factory methods в классах `Flux` и `Mono`.

Последовательность `String` — перечислить или взять из collection:

```java
Flux<String> seq1 = Flux.just("foo", "bar", "foobar");

List<String> iterable = Arrays.asList("foo", "bar", "foobar");
Flux<String> seq2 = Flux.fromIterable(iterable);
```

**Разбор операторов и ключей**

- `Flux.just` — cold `Flux` из явно перечисленных элементов.
- `Flux.fromIterable` — `Flux` из `Iterable`.

Другие factory methods:

```java
Mono<String> noData = Mono.empty(); (1)

Mono<String> data = Mono.just("foo");

Flux<Integer> numbersFromFiveToSeven = Flux.range(5, 3); (2)
```

**Разбор операторов и ключей**

- `Mono.empty()` — пустой `Mono`, generic type сохраняется.
- `Mono.just` — `Mono` с одним значением.
- `Flux.range(5, 3)` — старт 5, количество 3 → 5, 6, 7.

При subscribe `Flux` и `Mono` используют Java 8 lambdas. Много вариантов `.subscribe()`:

```java
subscribe(); (1)

subscribe(Consumer<? super T> consumer); (2)

subscribe(Consumer<? super T> consumer,
          Consumer<? super Throwable> errorConsumer); (3)

subscribe(Consumer<? super T> consumer,
          Consumer<? super Throwable> errorConsumer,
          Runnable completeConsumer); (4)

subscribe(Consumer<? super T> consumer,
          Consumer<? super Throwable> errorConsumer,
          Runnable completeConsumer,
          Consumer<? super Subscription> subscriptionConsumer); (5)
```

**Разбор операторов и ключей**

- `subscribe()` — подписка и запуск sequence.
- `Consumer<? super T>` — каждое значение.
- `errorConsumer` — реакция на `onError`.
- `completeConsumer` (`Runnable`) — успешный `onComplete`.
- `subscriptionConsumer` — работа с `Subscription` этой подписки.

Эти варианты возвращают ссылку на subscription, которой можно отменить подписку. При cancellation source должен перестать производить значения и почистить ресурсы. Это поведение в Reactor представлено интерфейсом `Disposable`.

### 1. `subscribe` Method Examples

```java
Flux<Integer> ints = Flux.range(1, 3); (1)
ints.subscribe(); (2)
```

**Разбор операторов и ключей**

- `Flux.range(1, 3)` — три значения при attach subscriber.
- `subscribe()` — простейшая подписка (видимого вывода нет, но `Flux` производит три значения).

```java
Flux<Integer> ints = Flux.range(1, 3); (1)
ints.subscribe(i -> System.out.println(i)); (2)
```

**Разбор операторов и ключей**

- `subscribe(Consumer)` — печать каждого значения.

Вывод:

```text
1
2
3
```

Намеренная ошибка:

```java
Flux<Integer> ints = Flux.range(1, 4) (1)
      .map(i -> { (2)
        if (i <= 3) return i; (3)
        throw new RuntimeException("Got to 4"); (4)
      });
ints.subscribe(i -> System.out.println(i), (5)
      error -> System.err.println("Error: " + error));
```

**Разбор операторов и ключей**

- `Flux.range(1, 4)` — четыре значения.
- `map` — трансформация; для 4 бросает `RuntimeException`.
- `subscribe(onNext, onError)` — обработчик ошибки.

```text
1
2
3
Error: java.lang.RuntimeException: Got to 4
```

Сигнатура с completion:

```java
Flux<Integer> ints = Flux.range(1, 4); (1)
ints.subscribe(i -> System.out.println(i),
    error -> System.err.println("Error " + error),
    () -> System.out.println("Done")); (2)
```

**Разбор операторов и ключей**

- `subscribe(..., ..., Runnable)` — handler `onComplete`.
- `onError` и `onComplete` взаимоисключающие terminal events.

```text
1
2
3
4
Done
```

### 2. Cancelling a `subscribe()` with Its `Disposable`

Все lambda-варианты `subscribe()` возвращают `Disposable`. Интерфейс означает, что subscription можно *cancel* методом `dispose()`.

Для `Flux`/`Mono` cancellation — сигнал source остановить производство. Это **не** гарантированно мгновенно: некоторые sources производят так быстро, что успевают complete до получения cancel.

Утилиты в классе `Disposables`: `Disposables.swap()` создаёт wrapper, позволяющий атомарно cancel и заменить конкретный `Disposable` (например UI: отменить запрос и заменить новым по клику). Dispose самого wrapper закрывает его: текущее значение и все будущие replacements.

`Disposables.composite(…)` собирает несколько `Disposable` и dispose их разом. После `dispose()` композита любая попытка add сразу dispose новый элемент.

### 3. An Alternative to Lambdas: `BaseSubscriber`

Есть более общий `subscribe`, принимающий полноценный `Subscriber`. Чтобы его писать проще, есть расширяемый класс `BaseSubscriber`.

Экземпляры `BaseSubscriber` (и subclasses) — **single-use**: если тот же instance подписать на второй `Publisher`, он cancel первую subscription. Повторное использование нарушило бы правило Reactive Streams: `onNext` не должен вызываться параллельно. Поэтому anonymous implementations нормальны только если объявлены прямо в `Publisher#subscribe(Subscriber)`.

```java
SampleSubscriber<Integer> ss = new SampleSubscriber<Integer>();
Flux<Integer> ints = Flux.range(1, 4);
ints.subscribe(ss);
```

**Разбор операторов и ключей**

- `SampleSubscriber` — user `BaseSubscriber`.
- `Flux.range` + `subscribe(Subscriber)`.

```java
import org.reactivestreams.Subscription;

import reactor.core.publisher.BaseSubscriber;

public class SampleSubscriber<T> extends BaseSubscriber<T> {

	@Override
	public void hookOnSubscribe(Subscription subscription) {
		System.out.println("Subscribed");
		request(1);
	}

	@Override
	public void hookOnNext(T value) {
		System.out.println(value);
		request(1);
	}
}
```

**Разбор операторов и ключей**

- `BaseSubscriber` — рекомендуемый abstract class для user-defined `Subscriber`.
- `hookOnSubscribe` — после `onSubscribe`; здесь `request(1)` (bounded demand).
- `hookOnNext` — обработка значения и ещё `request(1)`.
- По умолчанию `BaseSubscriber` делает unbounded request как `subscribe()`.

```text
Subscribed
1
2
3
4
```

Также есть `requestUnbounded()` (эквивалент `request(Long.MAX_VALUE)`) и `cancel()`. Дополнительные hooks: `hookOnComplete`, `hookOnError`, `hookOnCancel`, `hookFinally` (всегда при termination, тип — `SignalType`).

Почти наверняка нужны `hookOnError`, `hookOnCancel`, `hookOnComplete`; часто и `hookFinally`. `SampleSubscriber` — абсолютный минимум *bounded requests*.

### 4. Subscription Patterns

В типичном reactive-приложении пользователи редко явно вызывают `subscribe()`. Они декларативно определяют pipeline как `Flux`/`Mono` и отдают библиотеке (`reactor-netty`) или фреймворку (`Spring WebFlux`) для hook в non-blocking engine (обычно event loop). Логика — цепочка operators.

Иногда subscribe нужен явно.

#### 4.1. Fire-and-forget

Если игнорировать `Disposable` от `subscribe()`, теряется возможность cancel. Обычно anti-pattern, но на императивных границах (event listeners) неизбежно — тогда явно обрабатывайте ошибки.

```java
Mono<Void> handle(T arg) {
  sideEffectService.doSomething(arg).subscribe(); (1)
  return Mono.empty();
}
```

**Разбор операторов и ключей**

- `subscribe()` без сохранения `Disposable` — downstream не координирует cancellation/errors inner subscription.
- `Mono.empty()` — пустой результат handler.

Предпочтительно compose и вернуть:

```java
Mono<Void> handle(T arg) {
  return sideEffectService.doSomething(arg).then();
}
```

**Разбор операторов и ключей**

- `then()` — дождаться completion inner `Mono`, вернуть `Mono<Void>`.

На императивной границе — overload с error consumer:

```java
public void onEvent(T arg) {
  sideEffectService.doSomething(arg)
      .subscribe(ignored -> { }, e -> logger.warn("Operation failed", e));
}
```

**Разбор операторов и ключей**

- `subscribe(onNext, onError)` — логирование ошибки, чтобы не потерять `onError`.

#### 4.2. Dispatch and reference

Более устойчивый вариант — сохранить `Disposable` и `dispose()` позже.

```java
AtomicReference<Disposable> disposableRef = new AtomicReference<>();
Disposable disposable = sideEffectService.doSomething(arg)
    .subscribe(
        result -> { /* handle next */ },
        e -> logger.warn("Operation failed", e)
    );

disposableRef.set(disposable);

// during cleanup
disposableRef.get().dispose();
```

**Разбор операторов и ключей**

- `AtomicReference<Disposable>` — потокобезопасное хранение.
- `subscribe` возвращает `Disposable`.
- `dispose()` — cancel на cleanup.

#### 4.3. Use `BaseSubscriber`

Третий вариант (максимум контроля) — расширить `BaseSubscriber`: тонкий backpressure, custom cancellation, все lifecycle signals в одном классе. Полный пример — выше.

### 5. On Backpressure and Ways to Reshape Requests

В Reactor давление consumer распространяется к source отправкой `request` upstream operator. Сумма текущих requests — «demand» / «pending request». Demand ограничен `Long.MAX_VALUE` — unbounded request («производи так быстро, как можешь» — фактически отключение backpressure).

Первый request приходит от финального subscriber в момент subscription, но самые прямые способы подписки сразу делают unbounded `Long.MAX_VALUE`:

- `subscribe()` и большинство lambda-вариантов (кроме варианта с `Consumer<Subscription>`)
- `block()`, `blockFirst()`, `blockLast()`
- итерация `toIterable()` / `toStream()`

Простейшая кастомизация — `subscribe` с `BaseSubscriber` и override `hookOnSubscribe`:

```java
Flux.range(1, 10)
    .doOnRequest(r -> System.out.println("request of " + r))
    .subscribe(new BaseSubscriber<Integer>() {

      @Override
      public void hookOnSubscribe(Subscription subscription) {
        request(1);
      }

      @Override
      public void hookOnNext(Integer integer) {
        System.out.println("Cancelling after having received " + integer);
        cancel();
      }
    });
```

**Разбор операторов и ключей**

- `Flux.range(1, 10)` — source.
- `doOnRequest` — side-effect на каждый upstream request.
- `hookOnSubscribe` + `request(1)` — demand 1.
- `hookOnNext` + `cancel()` — отмена после первого элемента.

```text
request of 1
Cancelling after having received 1
```

При манипуляции request нужно производить достаточно demand, иначе Flux «застрянет». Поэтому `BaseSubscriber` по умолчанию unbounded в `hookOnSubscribe`. Переопределяя hook, обычно вызовите `request` хотя бы раз.

#### 5.1. Operators that Change the Demand from Downstream

Demand на уровне subscribe **может** быть переформирован каждым operator вверх по цепи. Учебный случай — `buffer(N)`: `request(2)` читается как спрос на **два полных buffer**. Значит request становится `2 x N`.

У некоторых operators есть параметр `prefetch` — они тоже меняют downstream request. Обычно это operators с inner sequences, выводящие `Publisher` из каждого элемента (`flatMap`).

**Prefetch** настраивает начальный request к inner sequences. Если не указан, большинство стартуют с demand `32`.

Обычно есть **replenishing optimization**: увидев 75% prefetch выполненным, operator повторно request 75% у upstream — эвристика упреждения.

Напрямую tune request: `limitRate` и `limitRequest`.

`limitRate(N)` дробит downstream requests на меньшие batch. `request(100)` к `limitRate(10)` → не более 10 requests по 10. В этой форме `limitRate` реализует replenishing.

Вариант `limitRate(highTide, lowTide)` настраивает replenishing (`lowTide`). `lowTide` 0 — **строгие** batch размера `highTide`.

`limitRequest(N)` **ограничивает** суммарный downstream demand до `N`. Если одиночный `request` не превышает `N`, он целиком идёт upstream. После эмиссии `N` элементов `limitRequest` считает sequence complete, шлёт `onComplete` и cancel source.


## Programmatically creating a sequence (Программное создание последовательности)

В этом разделе — создание `Flux` или `Mono` программным определением событий (`onNext`, `onError`, `onComplete`). Все эти методы дают API для триггера событий, который называют **sink**. Есть несколько вариантов sink.

(Страницы `producing.html` и `coreFeatures/programmatically-creating-sequence.html` в официальном Antora-гайде содержат один и тот же материал.)

### 1. Synchronous `generate`

Простейшая форма — метод `generate` с generator function. Это для синхронных one-by-one emissions: sink — `SynchronousSink`, `next()` можно вызвать не более одного раза за callback. Дополнительно можно `error(Throwable)` или `complete()` (опционально).

Самый полезный вариант позволяет хранить state, к которому обращаются в sink, чтобы решить, что эмитить дальше. Generator тогда — `BiFunction<S, SynchronousSink<T>, S>`; нужен `Supplier<S>` для начального state; функция возвращает новый state на каждом круге.

```java
Flux<String> flux = Flux.generate(
    () -> 0, (1)
    (state, sink) -> {
      sink.next("3 x " + state + " = " + 3*state); (2)
      if (state == 10) sink.complete(); (3)
      return state + 1; (4)
});
```

**Разбор операторов и ключей**

- `Flux.generate(Supplier, BiFunction)` — синхронная генерация с state.
- `() -> 0` — начальный state 0.
- `sink.next` — один элемент за вызов (`SynchronousSink`).
- `sink.complete()` — завершение при state 10.
- `return state + 1` — следующий state.

Вывод (таблица умножения на 3):

```text
3 x 0 = 0
3 x 1 = 3
3 x 2 = 6
3 x 3 = 9
3 x 4 = 12
3 x 5 = 15
3 x 6 = 18
3 x 7 = 21
3 x 8 = 24
3 x 9 = 27
3 x 10 = 30
```

Мутабельный state (`AtomicLong`):

```java
Flux<String> flux = Flux.generate(
    AtomicLong::new, (1)
    (state, sink) -> {
      long i = state.getAndIncrement(); (2)
      sink.next("3 x " + i + " = " + 3*i);
      if (i == 10) sink.complete();
      return state; (3)
});
```

**Разбор операторов и ключей**

- `AtomicLong::new` — мутабельный state.
- `getAndIncrement` — мутация.
- `return state` — тот же instance как новый state.

Если state требует cleanup, используйте `generate(Supplier<S>, BiFunction, Consumer<S>)`:

```java
Flux<String> flux = Flux.generate(
    AtomicLong::new,
      (state, sink) -> { (1)
      long i = state.getAndIncrement(); (2)
      sink.next("3 x " + i + " = " + 3*i);
      if (i == 10) sink.complete();
      return state; (3)
      }, (state) -> System.out.println("state: " + state)); (4)
```

**Разбор операторов и ключей**

- Третий аргумент `Consumer<S>` видит последний state (11) — место закрыть connection или иной resource.

### 2. Asynchronous and Multi-threaded: `create`

`create` — более продвинутая форма: несколько emissions за round, даже с нескольких threads. Экспонирует `FluxSink` с `next`, `error`, `complete`. В отличие от `generate`, нет state-based варианта; зато callback может триггерить multi-threaded events.

`create` полезен, чтобы bridge существующий API (например listener-based async API) в reactive-мир.

`create` **не** параллелизует ваш код и **не** делает его асинхронным, хотя его можно использовать с async API. Если блокировать внутри lambda `create`, возможны deadlocks. Даже с `subscribeOn` длинный blocking `create` (бесконечный цикл `sink.next(t)`) может заблокировать pipeline: requests не выполнятся, потому что цикл голодает тот же thread. Используйте `subscribeOn(Scheduler, false)`: `requestOnSeparateThread = false` — thread Scheduler для `create`, а `request` на исходном thread.

Listener с chunk и terminal event:

```java
interface MyEventListener<T> {
    void onDataChunk(List<T> chunk);
    void processComplete();
}
```

```java
Flux<String> bridge = Flux.create(sink -> {
    myEventProcessor.register( (4)
      new MyEventListener<String>() { (1)
        public void onDataChunk(List<String> chunk) {
          for(String s : chunk) {
            sink.next(s); (2)
          }
        }

        public void processComplete() {
            sink.complete(); (3)
        }
    });
});
```

**Разбор операторов и ключей**

- `Flux.create` — bridge к `MyEventListener`.
- `sink.next` — каждый элемент chunk становится элементом `Flux`.
- `sink.complete` — `processComplete` → `onComplete`.
- Регистрация выполняется асинхронно, когда работает `myEventProcessor`.

Поскольку `create` управляет backpressure, можно указать `OverflowStrategy`:

- `IGNORE` — полностью игнорировать downstream requests (возможен `IllegalStateException` при полных очередях).
- `ERROR` — `IllegalStateException`, если downstream не успевает.
- `DROP` — дропать входящий сигнал, если downstream не готов.
- `LATEST` — downstream получает только latest signals.
- `BUFFER` (default) — буферизовать все сигналы (unbounded, риск `OutOfMemoryError`).

У `Mono` тоже есть `create`. `MonoSink` не позволяет несколько emissions: сигналы после первого дропаются.

### 3. Asynchronous but single-threaded: `push`

`push` — середина между `generate` и `create` для событий от одного producer. Как `create`, может быть асинхронным и управлять backpressure теми же `OverflowStrategy`. Но только один producing thread может вызывать `next`, `complete` или `error` в один момент.

```java
Flux<String> bridge = Flux.push(sink -> {
    myEventProcessor.register(
      new SingleThreadEventListener<String>() { (1)
        public void onDataChunk(List<String> chunk) {
          for(String s : chunk) {
            sink.next(s); (2)
          }
        }

        public void processComplete() {
            sink.complete(); (3)
        }

        public void processError(Throwable e) {
            sink.error(e); (4)
        }
    });
});
```

**Разбор операторов и ключей**

- `Flux.push` — single-thread producer.
- `sink.next` / `complete` / `error` — с одного listener thread.

#### 3.1. A hybrid push/pull model

Большинство operators, включая `create`, следуют hybrid push/pull: обработка в основном async (push), но есть pull-компонент — `request`. Consumer не получит данные, пока не request. Source пушит, когда данные готовы, но в пределах requested amount.

`push()` и `create()` позволяют `onRequest` consumer, чтобы пушить в sink только при pending request.

```java
Flux<String> bridge = Flux.create(sink -> {
    myMessageProcessor.register(
      new MyMessageListener<String>() {

        public void onMessage(List<String> messages) {
          for(String s : messages) {
            sink.next(s); (3)
          }
        }
    });
    sink.onRequest(n -> {
        List<String> messages = myMessageProcessor.getHistory(n); (1)
        for(String s : messages) {
           sink.next(s); (2)
        }
    });
});
```

**Разбор операторов и ключей**

- `sink.onRequest` — poll при request.
- `getHistory(n)` — сразу доступные сообщения.
- Поздние async messages тоже через `sink.next`.

#### 3.2. Cleaning up after `push()` or `create()`

`onDispose` и `onCancel` — cleanup при cancellation или termination. `onDispose` — когда `Flux` complete, error или cancelled. `onCancel` — действие, специфичное для cancel, до cleanup `onDispose`.

```java
Flux<String> bridge = Flux.create(sink -> {
    sink.onRequest(n -> channel.poll(n))
        .onCancel(() -> channel.cancel()) (1)
        .onDispose(() -> channel.close()) (2)
});
```

**Разбор операторов и ключей**

- `onRequest` — `channel.poll(n)`.
- `onCancel` — только cancel signal, вызывается первым.
- `onDispose` — complete, error или cancel.

### 4. `handle`

`handle` — instance method, цепляется к существующему source (как обычные operators). Есть у `Mono` и `Flux`. Близок к `generate`: `SynchronousSink`, one-by-one. Но `handle` может из каждого source-элемента сгенерировать произвольное значение или пропустить элемент — комбинация `map` и `filter`.

```java
Flux<R> handle(BiConsumer<T, SynchronousSink<R>>);
```

Reactive Streams запрещает `null` в sequence. Если `map` вызывает preexisting method, иногда возвращающий `null`:

```java
public String alphabet(int letterNumber) {
	if (letterNumber < 1 || letterNumber > 26) {
		return null;
	}
	int letterIndexAscii = 'A' + letterNumber - 1;
	return "" + (char) letterIndexAscii;
}
```

```java
Flux<String> alphabet = Flux.just(-1, 30, 13, 9, 20)
    .handle((i, sink) -> {
        String letter = alphabet(i); (1)
        if (letter != null) (2)
            sink.next(letter); (3)
    });

alphabet.subscribe(System.out::println);
```

**Разбор операторов и ключей**

- `Flux.just` — входные номера.
- `handle` — `SynchronousSink`; `null` не эмитится.
- `sink.next(letter)` только если letter != null (filter+map).

Вывод: `M`, `I`, `T`.

## Threading and Schedulers (Потоки и Schedulers)

Reactor, как и Rx, может быть concurrency-agnostic: не форсирует модель concurrency. Однако в реальности нужна некоторая concurrency. Как переключаться? Через `Scheduler`.

`Scheduler` — abstraction планирования работы: можно сразу выполнить, отложить, периодически повторять, на выбранном `ExecutorService` / `ScheduledExecutorService`. Реализации в `Schedulers`:

- `Schedulers.immediate()` — текущий thread (no-op / trampoline-like immediate).
- `Schedulers.single()` — переиспользуемый одиночный thread.
- `Schedulers.parallel()` — фиксированный пул, оптимизированный для CPU-bound (число workers ≈ числу ядер).
- `Schedulers.boundedElastic()` — bounded elastic пул для blocking I/O; создаёт threads по требованию, reuse, с cap; после idle threads освобождаются. Предпочтительная замена устаревшему `elastic()`.
- `Schedulers.fromExecutorService(ExecutorService)` — обернуть существующий пул (осторожно: вы теряете гарантии Reactor).

Два ключевых operators переключения контекста: `publishOn` и `subscribeOn`.

### 1. The `publishOn` Method

`publishOn` применяется как любой operator в середине цепи. Он влияет на то, **где исполняется последующая часть цепи** (downstream от него): сигналы `onNext`/`onComplete`/`onError` доставляются на указанном `Scheduler`.

```java
Scheduler s = Schedulers.newParallel("parallel-scheduler", 4); (1)

final Flux<String> flux = Flux
    .range(1, 2)
    .map(i -> 10 + i)  (2)
    .publishOn(s)  (3)
    .map(i -> "value " + i);  (4)

new Thread(() -> flux.subscribe(System.out::println));  (5)
```

**Разбор операторов и ключей**

- `Schedulers.newParallel` — dedicated parallel `Scheduler` с 4 workers.
- `Flux.range` + `map` — выполняются на thread subscribe (здесь anonymous `Thread`).
- `publishOn(s)` — переключает остаток цепи на `s`.
- Второй `map` и `subscribe` consumer — на parallel-scheduler.

### 2. The `subscribeOn` Method

`subscribeOn` влияет на **процесс subscription**, когда backward chain строится: source и operators **выше по цепи до первого `publishOn`** исполняются на заданном `Scheduler`. Неважно, где в цепи стоит `subscribeOn` (в отличие от `publishOn`).

```java
Scheduler s = Schedulers.newParallel("parallel-scheduler", 4); (1)

final Flux<String> flux = Flux
    .range(1, 2)
    .map(i -> 10 + i)  (2)
    .subscribeOn(s)  (3)
    .map(i -> "value " + i);  (4)

new Thread(() -> flux.subscribe(System.out::println)); (5)
```

**Разбор операторов и ключей**

- `subscribeOn(s)` — вся подписка и source (`range`, оба `map`) на `s`, даже если `subscribe` вызван с другого thread.

`publishOn` и `subscribeOn` можно комбинировать. Несколько `publishOn` переключают контекст поэтапно. Несколько `subscribeOn` — обычно побеждает ближайший к source.

Дополнительно: `Schedulers` extra в appendix (virtual time, `ExecutorScheduler` и т.д.).

## Handling Errors (Обработка ошибок)

Для быстрого обзора operators error handling см. operator decision tree в appendix.

В Reactive Streams ошибки — **terminal events**. Как только ошибка произошла, sequence останавливается и ошибка идёт вниз по operators к последнему шагу — `Subscriber` и его `onError`.

Такие ошибки всё равно нужно обрабатывать на уровне приложения (уведомление в UI, error payload REST). Поэтому `onError` у subscriber **всегда должен быть определён**. Если нет — `onError` бросает `UnsupportedOperationException`. Можно детектировать через `Exceptions.isErrorCallbackNotImplemented`.

Reactor также даёт error-handling operators в середине цепи:

```java
Flux.just(1, 2, 0)
    .map(i -> "100 / " + i + " = " + (100 / i)) //this triggers an error with 0
    .onErrorReturn("Divided by zero :("); // error handling example
```

**Разбор операторов и ключей**

- `Flux.just` — 1, 2, 0.
- `map` — деление; на 0 ArithmeticException.
- `onErrorReturn` — статическое fallback-значение вместо ошибки.

Любая ошибка в reactive sequence — terminal event. Даже error-handling operator **не продолжает исходную sequence**. Он превращает `onError` в **старт новой** (fallback) sequence: заменяет terminated sequence upstream.

Ниже — параллели с `try`/`catch`.

### 1. Error Handling Operators

Знакомые способы в try-catch:

- Catch и вернуть static default.
- Catch и альтернативный путь (fallback method).
- Catch и динамически вычислить fallback.
- Catch, wrap в `BusinessException`, re-throw.
- Catch, залогировать, re-throw.
- `finally` / try-with-resources.

У всех есть эквиваленты-operators. `onError` в конце цепи ≈ `catch`:

```java
Flux<String> s = Flux.range(1, 10)
    .map(v -> doSomethingDangerous(v)) (1)
    .map(v -> doSecondTransform(v)); (2)
s.subscribe(value -> System.out.println("RECEIVED " + value), (3)
            error -> System.err.println("CAUGHT " + error) (4)
);
```

**Разбор операторов и ключей**

- `map(doSomethingDangerous)` — может бросить.
- Второй `map` — только если всё ок.
- `subscribe(onNext, onError)` — значения / ошибка.

Императивный аналог:

```java
try {
    for (int i = 1; i < 11; i++) {
        String v1 = doSomethingDangerous(i); (1)
        String v2 = doSecondTransform(v1); (2)
        System.out.println("RECEIVED " + v2);
    }
} catch (Throwable t) {
    System.err.println("CAUGHT " + t); (3)
}
```

#### 1.1. Static Fallback Value

Эквивалент «catch и static default» — `onErrorReturn`.

```java
try {
  return doSomethingDangerous(10);
}
catch (Throwable error) {
  return "RECOVERED";
}
```

```java
Flux.just(10)
    .map(this::doSomethingDangerous)
    .onErrorReturn("RECOVERED");
```

**Разбор операторов и ключей**

- `onErrorReturn("RECOVERED")` — при любой ошибке эмитить это значение и complete.

С `Predicate` на exception:

```java
Flux.just(10)
    .map(this::doSomethingDangerous)
    .onErrorReturn(e -> e.getMessage().equals("boom10"), "recovered10"); (1)
```

**Разбор операторов и ключей**

- `onErrorReturn(Predicate, value)` — recover только если message `"boom10"`.

#### 1.2. Catch and swallow the error

Если fallback-значение не нужно, а нужно игнорировать ошибку и пропустить уже произведённые элементы — заменить `onError` на `onComplete`: operator `onErrorComplete`.

```java
Flux.just(10,20,30)
    .map(this::doSomethingDangerousOn30)
    .onErrorComplete(); (1)
```

**Разбор операторов и ключей**

- `onErrorComplete` — `onError` → `onComplete`.
- Есть варианты с class / `Predicate` фильтрации exceptions.

#### 1.3. Fallback Method

Если нужен не один default, а альтернативный (более безопасный) путь — `onErrorResume` («catch и fallback method»).

Пример: nominal process тянет данные из внешнего ненадёжного сервиса, есть более stale, но надёжный local cache:

```java
Flux.just("key1", "key2")
    .flatMap(k -> callExternalService(k)
        .onErrorResume(e -> getFromCache(k))
    );
```

**Разбор операторов и ключей**

- `flatMap` — на каждый key inner `Publisher`.
- `onErrorResume` — при ошибке inner переключиться на `getFromCache`.

`onErrorResume` принимает `Function<Throwable, Publisher<T>>` — можно ветвить по типу ошибки.

#### 1.4. Dynamic Fallback Value

Динамический fallback — тоже `onErrorResume` (или `onErrorReturn` с вычислением вне, но типично resume на `Mono.just(computed)`).

```java
Flux.just(10)
    .map(this::doSomethingDangerous)
    .onErrorResume(error -> Mono.just(myFallbackService.getDefault()));
```

**Разбор операторов и ключей**

- `onErrorResume` + `Mono.just` — динамически полученное значение.

#### 1.5. Catch and Rethrow

Wrap и rethrow:

```java
Flux.just(10)
    .map(this::doSomethingDangerous)
    .onErrorMap(original -> new BusinessException("oops", original));
```

**Разбор операторов и ключей**

- `onErrorMap` — преобразовать `Throwable` в другой, сохранить terminal характер.

#### 1.6. Log or React on the Side

Побочный эффект без смены ошибки: `doOnError` (из семейства `doOn*`).

```java
Flux.just(10)
    .map(this::doSomethingDangerous)
    .doOnError(error -> log.error("failed", error))
    .onErrorReturn("RECOVERED");
```

**Разбор операторов и ключей**

- `doOnError` — side-effect (лог), ошибка идёт дальше.
- `onErrorReturn` — затем recover.

#### 1.7. Using Resources and the Finally Block

Эквиваленты `finally` / try-with-resources:

- `doFinally(Consumer<SignalType>)` — вызывается при любом termination (`ON_COMPLETE`, `ON_ERROR`, `CANCEL`).
- `using` / `usingWhen` — factory, привязывающая resource к жизненному циклу sequence (reactive try-with-resources).

```java
AtomicBoolean isDisposed = new AtomicBoolean();
Disposable disposableInstance = () -> isDisposed.compareAndSet(false, true);
Flux<String> flux =
Flux.using(
        () -> disposableInstance, (1)
        disposable -> Flux.just(disposable.toString()), (2)
        Disposable::dispose (3)
);
```

**Разбор операторов и ключей**

- `Flux.using(resourceSupplier, sourceFactory, resourceCleanup)` — получить resource, построить `Publisher`, cleanup при конце.

#### 1.8. Demonstrating the Terminal Aspect of `onError`

После ошибки исходная sequence не продолжается. `onErrorReturn` заменяет её fallback sequence из одного значения.

#### 1.9. Retrying

`retry()` / `retry(n)` реподписываются на source при ошибке (cold source начнётся заново). `retryWhen(Retry)` — декларативная политика (backoff, jitter) из `reactor-core` (`Retry.backoff`, `Retry.max` и т.д.).

```java
Flux.interval(Duration.ofMillis(250))
    .map(i -> {
        if (i < 3) return i;
        throw new RuntimeException("Got to 4");
    })
    .retry(1)
    .elapsed()
    .subscribe(System.out::println, System.err::println);
```

**Разбор операторов и ключей**

- `Flux.interval` — периодические Long.
- `map` — ошибка при i >= 3.
- `retry(1)` — одна повторная подписка.
- `elapsed` — пары (duration since last, value).

### 2. Handling Exceptions in Operators or Functions

Если ошибка брошена *внутри* operator или user function (`map`, `filter`), Reactor ловит её и превращает в `onError`. Для checked exceptions используйте `Exceptions.propagate` / `Exceptions.unwrap` или обёртки вроде `Mono.fromCallable`.

Не бросайте из operators ошибки, которые должны быть *не терминальными* для всей цепи — используйте inner sequence + `onErrorResume` на нужном уровне.

## Processors and Sinks

Исторически Reactor имел `Processor` (`UnicastProcessor`, `DirectProcessor`, `EmitterProcessor`, `ReplayProcessor` и т.д.). Они **deprecated**. Рекомендуемая замена — `Sinks` API: программно эмитить в `Flux`/`Mono` с явными правилами concurrency и backpressure.

`Sinks.many()` строит `Sinks.Many<T>` (много значений, как `Flux`). `Sinks.one()` — `Sinks.One<T>` (0..1, как `Mono`). `Sinks.empty()` — только terminal signal.

Типичный паттерн:

```java
Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
Flux<String> flux = sink.asFlux();

sink.emitNext("hello", Sinks.EmitFailureHandler.FAIL_FAST);
sink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
```

**Разбор операторов и ключей**

- `Sinks.many().unicast().onBackpressureBuffer()` — unicast `Sinks.Many` с внутренним buffer.
- `asFlux()` — view как `Flux` для subscribers.
- `emitNext` / `emitComplete` + `EmitFailureHandler.FAIL_FAST` — безопасная эмиссия с политикой при неудаче (вместо «голого» `tryEmit*` без обработки).

### 1. Safely Produce from Multiple Threads by Using `Sinks.One` and `Sinks.Many`

Default flavors `Sinks` в reactor-core детектят multi-threaded usage и делают emission thread-safe (с retry/busy-loop там, где это специфицировано). Методы `tryEmitNext` возвращают `EmitResult` (`OK`, `FAIL_OVERFLOW`, `FAIL_ZERO_SUBSCRIBER`, `FAIL_TERMINATED`, `FAIL_NON_SERIALIZED`, `FAIL_CANCELLED`).

`emitNext(value, handler)` повторяет попытку согласно `EmitFailureHandler` (например `busyLooping(Duration)`).

### 2. Overview of Available Sinks

#### 2.1. `Sinks.many().unicast().onBackpressureBuffer(args?)`

Unicast `Sinks.Many` обрабатывает backpressure внутренним buffer. Только **один** subscriber. Полезно как мост из императивного кода.

#### 2.2. `Sinks.many().multicast().onBackpressureBuffer(args?)`

Multicast: несколько subscribers, backpressure соблюдается для каждого. Обычно есть внутренний buffer для «медленных»; семантика late subscribers — без replay истории (если не выбран replay-вариант).

#### 2.3. `Sinks.many().multicast().directAllOrNothing()`

Простой backpressure: если **любой** subscriber не может принять элемент, элемент не доставляется **никому**.

#### 2.4. `Sinks.many().multicast().directBestEffort()`

Best effort: медленный subscriber может пропустить элемент, быстрые получают.

#### 2.5. `Sinks.many().replay()`

Replay кеширует элементы и replay их late subscribers:

- `limit(int)` — ограниченная история
- `all()` — unbounded history
- `limit(Duration)` — time window
- `limit(int, Duration)` — размер + окно

Есть дополнительные overloads.

#### 2.6. `Sinks.unsafe().many()`

Для advanced users / builders operators: те же factories **без** extra producer thread safety.

#### 2.7. `Sinks.one()`

Простой `Sinks.One<T>` — не более одного значения, затем complete, либо error.

#### 2.8. `Sinks.empty()`

`Sinks.Empty<T>` — только complete или error, без `onNext`.


# Testing (Тестирование)

Модуль `reactor-test` (зависимость test scope) даёт `StepVerifier`, `TestPublisher`, `PublisherProbe` и virtual time.

## 1. Testing a Scenario with `StepVerifier`

Самый частый случай — есть `Flux`/`Mono` (часто из метода вашего кода) и нужно проверить сценарий: какие события, в каком порядке, с каким demand.

```java
public <T> Flux<T> appendBoomError(Flux<T> source) {
  return source.concatWith(Mono.error(new IllegalArgumentException("boom")));
}

@Test
public void testAppendBoomError() {
  Flux<String> source = Flux.just("foo", "bar");

  StepVerifier.create(
    appendBoomError(source))
    .expectNext("foo")
    .expectNext("bar")
    .expectErrorMessage("boom")
    .verify();
}
```

**Разбор операторов и ключей**

- `concatWith` — после элементов source добавить другой `Publisher`.
- `Mono.error` — `Publisher`, сразу `onError`.
- `StepVerifier.create` — обернуть sequence для теста.
- `expectNext` — ожидать `onNext` с этими значениями.
- `expectErrorMessage` — terminal `onError` с message `boom`.
- `verify()` — **подписать** и блокировать до завершения сценария (без `verify` ничего не происходит).

### 1.1. Better Identifying Test Failures

`StepVerifier` даёт опции точнее указать, какой expectation step упал: `as("description")` после шага и `StepVerifierOptions` / `withVirtualTime` с описанием. При failure сообщение включает description шага.

## 2. Manipulating Time

С time-based operators (`interval`, `delayElements`, `timeout`) не нужно ждать реальное время: virtual time.

```java
StepVerifier.withVirtualTime(() -> Mono.delay(Duration.ofDays(1)))
    .expectSubscription()
    .expectNoEvent(Duration.ofDays(1))
    .expectNext(0L)
    .verifyComplete();
```

**Разбор операторов и ключей**

- `StepVerifier.withVirtualTime(Supplier<Publisher>)` — source создаётся *после* установки virtual clock (важно: не создавать `interval` заранее).
- `Mono.delay` — один `0L` после duration.
- `expectNoEvent` — продвинуть virtual time, ожидая отсутствие сигналов (кроме subscription).
- `expectNext(0L)` + `verifyComplete`.

Альтернатива: `thenAwait(Duration)` продвигает clock. `expectNoEvent` падает, если за окно пришёл сигнал.

## 3. Performing Post-execution Assertions with `StepVerifier`

После финального expectation можно перейти к `verifyThenAssertThat()` и делать assertions о всей последовательности: dropped elements, discarded errors, duration и т.д. (API `StepVerifier.Assertions`).

## 4. Testing the `Context`

`StepVerifier` имеет expectation methods для `Context`: `expectAccessibleContext()`, `then()` и проверки ключей, установленных через `contextWrite` / `contextCapture`. Подробнее о `Context` — в Advanced Features.

## 5. Manually Emitting with `TestPublisher`

Для полного контроля над source: `TestPublisher<T>` реализует `Publisher` и позволяет императивно `next`, `error`, `complete`, а также нарушать spec (для тестов operators).

```java
TestPublisher<String> testPublisher = TestPublisher.create();
Flux<String> flux = testPublisher.flux().map(String::toUpperCase);

StepVerifier.create(flux)
    .then(() -> testPublisher.next("a", "b"))
    .expectNext("A", "B")
    .then(testPublisher::complete)
    .verifyComplete();
```

**Разбор операторов и ключей**

- `TestPublisher.create()` — ручной source.
- `flux()` — view как `Flux`.
- `map(String::toUpperCase)` — тестируемый operator.
- `then(Runnable)` — side-effect в ходе verify (эмиссия).
- `testPublisher.next` / `complete`.

## 6. Checking the Execution Path with `PublisherProbe`

Когда сложная цепь имеет несколько путей (`switchIfEmpty`, `onErrorResume`), `PublisherProbe` показывает, был ли путь subscribed / requested / cancelled.

```java
PublisherProbe<Void> probe = PublisherProbe.empty();
StepVerifier.create(
        Flux.empty().switchIfEmpty(probe.mono()))
    .verifyComplete();
probe.assertWasSubscribed();
```

**Разбор операторов и ключей**

- `PublisherProbe.empty()` — зонд-пустой `Publisher`.
- `switchIfEmpty(probe.mono())` — fallback путь.
- `assertWasSubscribed` — fallback действительно подписали.

# Debugging (Отладка)

## 1. The Typical Reactor Stack Trace

При сдвиге к async-коду stack traces усложняются: ошибка влезает на thread `Scheduler`, а не на thread сборки цепи. Типичный trace показывает внутренности operators (`FluxMapFuseable` и т.д.), но **не** строку, где вы собрали `map`/`flatMap`.

## 2. Activating Debug Mode — aka tracebacks

Самый простой (и самый медленный) способ — глобальный debug mode / operator stacktrace (traceback):

```java
Hooks.onOperatorDebug();
```

**Разбор операторов и ключей**

- `Hooks.onOperatorDebug()` — при **сборке** каждого operator сохраняется traceback assembly site. На ошибке он добавляется как suppressed / appendix к stack trace.

Включайте как можно раньше (static init). Есть overhead на каждый operator — для dev/staging, не идеально для плотного production (см. `ReactorDebugAgent`).

## 3. Reading a Stack Trace in Debug Mode

После активации в конце ошибки появляется секция *Assembly trace* / traceback: операторы в порядке сборки и ссылки на ваш исходный код (класс:строка), где цепь собрана.

### 3.1. The `checkpoint()` Alternative

Debug mode глобален. `checkpoint()` — локальная альтернатива: вставляется в конкретную цепь и запоминает assembly site (опционально с description). Меньше overhead, если ставить в ключевых местах.

```java
Flux.just(1)
    .map(i -> i / 0)
    .checkpoint("divider")
    .subscribe();
```

**Разбор операторов и ключей**

- `checkpoint("divider")` — именованная точка сборки; при ошибке description виден в traceback.
- `checkpoint(true)` / light checkpoints — компромисс стоимость/деталь.

## 4. Production-ready Global Debugging

Отдельный Java Agent (`reactor-tools` / `ReactorDebugAgent`) инструментирует код через ByteBuddy и добавляет traceback **без** runtime overhead `Hooks.onOperatorDebug()` на каждый subscribe.

```java
ReactorDebugAgent.init();
```

**Разбор операторов и ключей**

- `ReactorDebugAgent.init()` — self-attach агента (обычно в начале `main` или в тестах).

### 4.1. Limitations

Реализован как Java Agent + ByteBuddy self-attach. Некоторые environments (ограниченные security manager, native image, часть контейнеров) self-attach не позволяют.

### 4.2. Running `ReactorDebugAgent` as a Java Agent

Если self-attach недоступен, запускайте `reactor-tools` как обычный `-javaagent:path/to/reactor-tools.jar`.

### 4.3. Running `ReactorDebugAgent` at build time

Можно применить как build-time instrumentation (ByteBuddy gradle/maven plugin), чтобы не нужен runtime attach.

## 5. Logging a Sequence

Помимо stack traces, мощный инструмент — `log()` operator: логирует сигналы (`onSubscribe`, `request`, `onNext`, `onError`, `onComplete`, `cancel`) через `Logger` (по умолчанию category вида `reactor.Flux.Map.1`).

```java
Flux.range(1, 2)
    .log("range")
    .subscribe();
```

**Разбор операторов и ключей**

- `log("range")` — категория/префикс логов сигналов между `range` и `subscribe`.

# Metrics

## 1. Scheduler metrics

Каждая async-операция идёт через `Scheduler`. Можно обернуть scheduler в timed/metrics decorator (`Micrometer.timedScheduler` в модуле `reactor-core-micrometer`) и наблюдать queue, execution, scheduling delay.

## 2. Publisher metrics

Иногда нужны метрики на стадии pipeline. Вместо ручного `doOn*` используйте tap listener на базе Micrometer:

```java
Flux.range(1, 10)
    .name("range")
    .tap(Micrometer.metrics(meterRegistry))
    .subscribe();
```

**Разбор операторов и ключей**

- `name("range")` — имя sequence для meters.
- `tap(Micrometer.metrics(MeterRegistry))` — listener, пишущий meters (onNext counts, flow duration и т.д.).

### 2.1. Tags

Помимо common tags `Micrometer.metrics()`, можно добавить custom tags operator'ом `tag`:

```java
flux.name("orders").tag("region", "eu").tap(Micrometer.metrics(registry));
```

**Разбор операторов и ключей**

- `tag(key, value)` — Micrometer tag на последующие meters.

### 2.2. Observation

Альтернатива полным metrics — Micrometer `Observation` (`Micrometer.observation(ObservationRegistry)`): spans/observations вокруг reactive flow, интеграция с tracing.

## 3. Meters and tags for Reactor-Core-Micrometer module

Модуль документирует meters для:

### 3.1. `Micrometer.metrics()`

Счётчики/таймеры потока (requested, onNext, onError, onComplete, flow duration и др.) с tags имени и custom tags.

### 3.2. `Micrometer.timedScheduler()`

Meters обёрнутого `Scheduler`: submitted, completed, pending, task duration.

### 3.3. `Micrometer.observation()`

Observation-based meters/spans вместо (или вместе с) классических meters.

# Kotlin support

## 1. Requirements

Reactor поддерживает Kotlin 1.1+ и требует `kotlin-stdlib` (или `kotlin-stdlib-jdk7` / `kotlin-stdlib-jdk8`).

## 2. Extensions

С Dysprosium-M1 (`reactor-core 3.3.0.M1`) Kotlin extensions вынесены в отдельный артефакт `reactor-kotlin-extensions` (и связанные), чтобы не тянуть Kotlin в чистый Java classpath. Extensions дают idiomatic helpers (`toMono()`, `toFlux()`, reified generics, coroutine bridges в отдельных модулях).

```kotlin
import reactor.kotlin.core.publisher.toMono

val mono = "foo".toMono()
```

**Разбор операторов и ключей**

- `toMono()` — extension на значение/`Optional`/`CompletableFuture` и т.п. (в зависимости от import).

## 3. Null Safety

Одно из ключевых свойств Kotlin — null safety. Reactor аннотирует API (`@NonNull`, `@Nullable`, JSR-305 / JSpecify в зависимости от поколения), чтобы Kotlin видел platform types корректно. `null` по-прежнему запрещён в reactive sequence (Reactive Streams); пустое значение — `Mono.empty()` / отсутствие `onNext`.

# Advanced Features and Concepts (Продвинутые возможности)

Раздел собирает hot/cold, multicast, batching, `ParallelFlux`, transform, `Hooks`, `Context`, null-safety, cleanup и factory для `Scheduler`.

## Hot vs Cold

См. также введение. В Reactor большинство factory (`just`, `fromIterable`, `defer`, `generate`) — **cold**: каждый `Subscriber` получает свою подписку на source.

**Hot** источники: `Sinks` multicast/replay, `ConnectableFlux` после `connect()`, некоторые adapter'ы к listener API. Late subscriber не видит прошлые `onNext` (если нет replay).

`Flux.defer` остаётся cold, но supplier вызывается **на каждую** подписку — удобно для «свежего» cold source.

```java
Flux<Long> clock = Flux.interval(Duration.ofSeconds(1)); // cold: свой отсчёт на subscriber
Sinks.Many<Long> hot = Sinks.many().multicast().directBestEffort();
```

**Разбор операторов и ключей**

- `Flux.interval` — cold тики с 0 для каждого subscriber.
- `Sinks.many().multicast()` — hot: эмиссия не привязана 1:1 к одному subscriber.

## Broadcasting with `ConnectableFlux`

Чтобы сделать cold source hot (несколько subscribers делят одну подписку на source):

```java
Flux<Integer> source = Flux.range(1, 3)
    .doOnSubscribe(s -> System.out.println("subscribed to source"));

ConnectableFlux<Integer> co = source.publish();

co.subscribe(System.out::println);
co.subscribe(System.out::println);
co.connect();
```

**Разбор операторов и ключей**

- `publish()` — `ConnectableFlux` (multicast, без auto-connect).
- Два `subscribe` ещё не подписывают source.
- `connect()` — одна подписка на source, broadcast обоим.

Варианты: `publish().autoConnect(n)` — connect при n subscribers; `refCount(n)` — connect/disconnect по числу subscribers; `replay(n)` — `ConnectableFlux` с кешем.

## Three Sorts of Batching

Три семейства группировки:

1. **Buffering** — `buffer`, `bufferTimeout`, `bufferUntil`, `bufferWhen`: собирает `List` (или другую collection) и эмитит пачки как `Flux<List<T>>`.
2. **Windowing** — `window*`: эмитит `Flux<Flux<T>>` — каждое окно само является `Flux` (streaming windows, backpressure на окнах).
3. **Grouping** — `groupBy`: `Flux<GroupedFlux<K, T>>`, ключ + бесконечные (пока открыты) группы.

```java
Flux.range(1, 10)
    .buffer(5)
    .subscribe(System.out::println);
```

**Разбор операторов и ключей**

- `buffer(5)` — списки по 5 элементов: `[1..5]`, `[6..10]`.

```java
Flux.range(1, 10)
    .window(5)
    .flatMap(w -> w.reduce(0, Integer::sum))
    .subscribe(System.out::println);
```

**Разбор операторов и ключей**

- `window(5)` — два inner `Flux`.
- `flatMap` + `reduce` — сумма каждого окна.

```java
Flux.just(1, 3, 5, 2, 4, 6, 11, 12, 13)
    .groupBy(i -> i % 2 == 0 ? "even" : "odd")
    .concatMap(g -> g.take(2).map(i -> g.key() + i))
    .subscribe(System.out::println);
```

**Разбор операторов и ключей**

- `groupBy` — ключ even/odd, `GroupedFlux`.
- `concatMap` — последовательно обработать группы.
- `take(2)` — по два элемента из группы.
- `g.key()` — ключ группы.

## Parallelizing with `ParallelFlux`

`parallel()` делит sequence на «рельсы» (по умолчанию число CPU). Пока не вызван `runOn(Scheduler)`, работа остаётся sequential в смысле execution. `runOn` назначает `Scheduler` (обычно `Schedulers.parallel()`). Собрать обратно: `sequential()` или `then()`.

```java
Flux.range(1, 10)
    .parallel(2)
    .runOn(Schedulers.parallel())
    .map(i -> i * 10)
    .sequential()
    .subscribe(System.out::println);
```

**Разбор операторов и ключей**

- `parallel(2)` — 2 rails (`ParallelFlux`).
- `runOn` — исполнение rails на parallel `Scheduler`.
- `map` — независимо на каждом rail.
- `sequential()` — снова обычный `Flux` (порядок между rails не гарантирован как исходный, если не оговорено иначе).

## Mutualizing Operator Usage

Повторяющиеся куски цепи можно вынести:

- `transform(Function<Flux<T>, Publisher<R>>)` — применяется **на этапе сборки**, один раз; удобно для переиспользуемых operator chains.
- `transformDeferred` (ранее `compose`) — функция вызывается **на каждую подписку** (как `defer` + transform).

```java
Function<Flux<String>, Flux<String>> filterAndMap =
    f -> f.filter(color -> !color.equals("orange"))
          .map(String::toUpperCase);

Flux.fromIterable(Arrays.asList("blue", "green", "orange", "purple"))
    .doOnNext(System.out::println)
    .transform(filterAndMap)
    .subscribe(d -> System.out.println("Subscriber to Transformed MapAndFilter: "+d));
```

**Разбор операторов и ключей**

- `transform` — вставить переиспользуемую цепь `filter` + `map`.
- `filter` — отбросить `"orange"`.
- `map(String::toUpperCase)`.

## Hooks

`Hooks` — глобальные callbacks на сборку и сигналы:

- `onOperatorDebug` — traceback (см. Debugging).
- `onEachOperator` / `onLastOperator` — обернуть каждый / последний operator при сборке.
- `onErrorDropped` — ошибка, которую некуда доставить (уже terminated).
- `onNextDropped` — элемент после terminate.
- `onOperatorError` — ошибка внутри operator.

Используйте осторожно: глобальное состояние процесса.

## Context

`Context` — аналог `ThreadLocal` для реактивной цепи: immutable key-value store, который **пишется downstream → upstream** при subscribe и **читается** operators, которым нужны request-scoped данные (credentials, MDC-like keys) без нарушения Reactive Streams (в сигналах `onNext` нет места для такого metadata).

```java
String key = "message";
Mono<String> r = Mono.just("Hello")
    .flatMap(s -> Mono.deferContextual(ctx ->
         Mono.just(s + " " + ctx.get(key))))
    .contextWrite(ctx -> ctx.put(key, "World"));

StepVerifier.create(r)
            .expectNext("Hello World")
            .verifyComplete();
```

**Разбор операторов и ключей**

- `contextWrite` — положить ключ в `Context` (видимость **выше** по цепи от точки write, для подписки).
- `Mono.deferContextual` / `transformDeferredContextual` — прочитать `ContextView`.
- `ctx.get(key)` — значение `"World"`.
- Порядок важен: write должен быть **downstream** от чтения (ближе к `subscribe`).

`contextWrite` на разных уровнях вложенных подписок не всегда «просвечивает» автоматически во все inner — смотрите правила в официальном разделе (inner subscribers могут нуждаться в явном пробросе).

## Context Propagation

Модуль / интеграция с `io.micrometer:context-propagation` (и страница `advanced-contextPropagation.html`) позволяет мостить Reactor `Context` и `ThreadLocal` (MDC, Sleuth/Micrometer Observation, OpenTelemetry). Типичный паттерн: зарегистрировать `ThreadLocalAccessor`, использовать `contextCapture()` на границе imperative→reactive и `handle`/`tap` с restoration, либо авто-инструментацию в новых поколениях.

`Hooks.enableAutomaticContextPropagation()` (когда доступно в вашей версии) автоматически оборачивает scheduled tasks, чтобы ThreadLocals восстанавливались на workers `Scheduler`.

```java
Mono.deferContextual(ctx -> Mono.just(ctx.getOrDefault("foo", "n/a")))
    .contextWrite(Context.of("foo", "bar"));
```

**Разбор операторов и ключей**

- `deferContextual` — чтение.
- `Context.of` — factory immutable `Context`.
- `contextWrite` — установка для этой подписки.

## Null Safety

Reactor помечает API nullability-аннотациями. Reactive Streams **запрещает** `null` в `onNext`. Пустое = нет значения (`Mono.empty()`), не `just(null)` (это exception). Kotlin видит эти контракты (см. Kotlin support). В Java используйте `@NonNullApi` package defaults.

## Cleanup

Помимо `using` / `doFinally` / `onDispose`:

- Всегда `dispose()` долгоживущие `Scheduler`, которые вы создали (`Schedulers.newParallel` и т.д.), иначе threads утекут.
- `Sinks` после terminal не принимают новые emit.
- `Hooks.reset*()` в тестах, чтобы не протекал global state между cases.

## Scheduler Factory

`Schedulers` можно кастомизировать factory (`Schedulers.setFactory`, `onHandleError`, decorator). Это advanced: подмена default `boundedElastic`/`parallel`/`single` в целом процессе (тесты, metrics wrappers). После смены factory вызовите `Schedulers.resetFactory()` в cleanup тестов.

# FAQ

## 1. How Do I Wrap a Synchronous, Blocking Call?

Часто source синхронный и blocking (JDBC, legacy SDK). Не вызывайте его внутри `map` на `parallel()`/`single()` — заблокируете пул. Оберните:

```java
Mono.fromCallable(() -> blockingCall())
    .subscribeOn(Schedulers.boundedElastic());
```

**Разбор операторов и ключей**

- `Mono.fromCallable` — lazy вызов на subscribe, checked exceptions → `onError`.
- `subscribeOn(Schedulers.boundedElastic())` — blocking на elastic пуле, не на event-loop.

Альтернативы: `publishOn` перед blocking `map` (хуже читается), dedicated `Scheduler` из `fromExecutorService`.

## 2. I Used an Operator on my Flux but it Doesn’t Seem to Apply. What Gives?

Operators **возвращают новый** `Publisher`. Если вы написали:

```java
flux.map(String::toUpperCase);
flux.subscribe(System.out::println);
```

**Разбор операторов и ключей**

- Результат `map` отброшен; `subscribe` идёт на исходный `flux`. Нужно: `flux = flux.map(...)` или сразу цепочка.

См. официальный FAQ `faq.chain`.

## 3. My `Mono` `zipWith` or `zipWhen` is never called

`zip` / `zipWith` / `zipWhen` ждут **все** источники. Если один `Mono` пустой (`empty`) или ещё не эмитит, комбинация не произойдёт (пустой zip complete без значения, либо «висит» на never). Проверьте, что все стороны действительно эмитят ровно одно значение.

## 4. Using `zip` along with empty-completed publishers

`zip` завершается, когда **любой** источник complete, не набрав пары — остальные значения не ждутся. Empty + nonempty `zip` → empty. Для «взять что есть» нужны другие operators (`combineLatest` не из core zip semantics; или defaultIfEmpty).

## 5. How to Use `retryWhen` to Emulate `retry(3)`?

```java
source.retryWhen(Retry.max(3));
```

**Разбор операторов и ключей**

- `Retry.max(3)` — спецификация для `retryWhen`, эквивалент трёх повторов после ошибки (см. точные границы в javadoc `Retry`: число attempts vs retries).

Исторический ручной вид: `retryWhen(companion -> companion.take(3))` — companion эмитит ошибки; `take(3)` пропускает три, затем complete companion → stop retry.

## 6. How can I use `retryWhen` for Exponential Backoff?

```java
source.retryWhen(Retry.backoff(3, Duration.ofMillis(100)));
```

**Разбор операторов и ключей**

- `Retry.backoff(maxAttempts, minBackoff)` — экспоненциальная задержка между попытками, jitter по умолчанию.
- Настраивается `.maxBackoff`, `.jitter`, `.filter`, `.doBeforeRetry`.

## 7. How Do I Ensure Thread Affinity when I Use `publishOn()`?

`publishOn` переключает **последующие** operators на выбранный `Scheduler`, но не «приклеивает» навсегда весь процесс: следующий `publishOn` снова сменит thread. Для affinity держите UI/Netty loop `Scheduler` и ставьте `publishOn` непосредственно перед кодом, которому нужен этот thread; не ставьте другой `publishOn` после.

## 8. What Is a Good Pattern for Contextual Logging? (MDC)

Большинство logging frameworks имеют MDC (`ThreadLocal`). В async-цепи ThreadLocal теряется. Паттерн:

1. Класть correlation id в `Context` (`contextWrite`).
2. На границе логирования читать `Context` (`deferContextual`) и писать в лог явно **или**
3. Включить context-propagation + MDC accessor, чтобы на `publishOn`/`subscribeOn` MDC восстанавливался.

Не полагайтесь на «голый» MDC после `publishOn` без propagation.

# Appendix A: How to Read Marble Diagrams

Marble diagrams — стандарт визуализации Reactive Streams:

- Горизонтальная стрелка — время, слева направо.
- Кружки (marbles) на верхней линии — элементы **source** `Publisher`.
- Вертикальная черта `|` — `onComplete`.
- Крест `X` — `onError`.
- Нижняя линия — **результат** operator.
- Несколько верхних линий — несколько sources (`merge`, `zip`).
- Пунктир вниз от marble — как элемент трансформируется.

`Flux` vs `Mono` часто рисуют с разной «вместимостью» (много marbles vs один). В официальном HTML-гайде картинки marble встроены в страницы operators; в этом Markdown-переводе сохранены текстовые правила чтения.

# Appendix B: Which operator do I need?

Официальный appendix — decision tree. Ниже те же категории и типичные operators (имена на English).

**Создать последовательность**

- Из значений: `just`, `empty`, `error`, `never`, `fromIterable`, `fromArray`, `fromStream`, `range`.
- Из `Optional` / `CompletableFuture` / `CompletionStage`: `Mono.justOrEmpty`, `Mono.fromFuture`, `Mono.fromCompletionStage`.
- Программно: `generate`, `create`, `push`, `handle`, `Sinks`.
- Отложенно: `defer`, `deferContextual`.
- Время: `interval`, `Mono.delay`.

**Трансформировать элементы**

- 1:1: `map`, `cast`, `index`.
- 1:N async: `flatMap`, `flatMapSequential`, `concatMap`.
- Side-effect peek: `doOnNext`, `doOnEach`, `doOnSubscribe`, `doOnRequest`, `doOnCancel`, `doOnError`, `doOnComplete`, `doFinally`, `log`.
- Материализовать сигналы: `materialize`, `dematerialize`.

**Фильтровать / ограничить**

- `filter`, `filterWhen`, `ofType`, `distinct`, `distinctUntilChanged`.
- `take`, `takeLast`, `takeUntil`, `takeWhile`, `elementAt`, `next`, `single`, `singleOrEmpty`.
- `skip`, `skipLast`, `skipUntil`, `skipWhile`.
- `ignoreElements`, `ignoreThen`.

**Ошибки**

- `onErrorReturn`, `onErrorComplete`, `onErrorResume`, `onErrorMap`.
- `retry`, `retryWhen`, `timeout`.
- `doOnError`.

**Комбинировать**

- Последовательно: `concat`, `concatWith`, `startWith`, `then`, `thenMany`, `thenEmpty`.
- Слияние: `merge`, `mergeWith`, `mergeSequential`, `mergeComparing`.
- Пары: `zip`, `zipWith`, `zipWhen`, `combineLatest`.
- Логическое: `and` (когда ещё есть), `when`.

**Агрегировать**

- `reduce`, `scan`, `collectList`, `collectMap`, `collect`, `count`, `hasElements`, `all`, `any`.

**Batching**

- `buffer*`, `window*`, `groupBy`.

**Backpressure / demand**

- `limitRate`, `limitRequest`, `onBackpressureBuffer`, `onBackpressureDrop`, `onBackpressureLatest`, `onBackpressureError`.

**Потоки и время**

- `delayElements`, `delaySequence`, `elapsed`, `timestamp`, `timeout`, `windowTimeout`.

**Многопоточность**

- `publishOn`, `subscribeOn`, `parallel` + `runOn` + `sequential`, `publish`/`replay`/`share`.

**Hot / multicast**

- `publish`, `replay`, `share`, `cache`, `Sinks.many()`.

**Context**

- `contextWrite`, `deferContextual`, `transformDeferredContextual`.

Если не нашли operator — javadoc `Flux`/`Mono` и marble в HTML reference.

# Appendix C: Reactor Extra

Артефакт `reactor-extra` (и связанные addons) даёт дополнительные operators и `Scheduler`, не входящие в минимальный `reactor-core`:

- Math extras (`reactor.math`) — `sumDouble`, `computeXX` агрегаты.
- `reactor-extra` retry/repeat helpers исторически; часть переехала в core `Retry`.
- Extra `Scheduler` (см. ссылку в Getting Started / coreFeatures на `apdx-reactorExtra.html#extra-schedulers`): специализированные реализации сверх `parallel`/`boundedElastic`/`single`.
- Swing/SWT adapters, cache extras — в зависимости от версии addons.

Подключайте отдельной зависимостью; версии выравнивайте BOM `reactor-bom`.

# Источник и покрытие

Официальный гайд 3.8.6 — multi-page Antora (не одна HTML-страница). TOC и проза сверены со страницами:

- `aboutDoc.html`, `gettingStarted.html`, `reactiveProgramming.html`
- `coreFeatures.html`, `flux.html`, `mono.html`, `simple-ways-to-create-a-flux-or-mono-and-subscribe-to-it.html`
- `programmatically-creating-sequence.html` / `producing.html` (дубликат)
- `schedulers.html`, `error-handling.html`, `sinks.html`
- `testing.html`, `debugging.html`, `metrics.html`, `kotlin.html`
- `advancedFeatures.html` + hotCold, ConnectableFlux, batching, ParallelFlux, transform, hooks, context, null-safety, cleanup, scheduler-factory
- `advanced-contextPropagation.html`
- `faq.html`, `apdx-howtoReadMarbles.html`, `apdx-operatorChoice.html`, `apdx-reactorExtra.html`

Картинки marble/диаграмм из HTML в Markdown не встроены (бинарные assets). Полные таблицы всех Micrometer meter names/tags — в HTML `metrics.html`; здесь описаны группы meters и API.

