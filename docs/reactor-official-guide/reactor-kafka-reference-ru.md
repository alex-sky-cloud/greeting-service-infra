> **Источник / source:** [https://projectreactor.io/docs/kafka/release/reference/](https://projectreactor.io/docs/kafka/release/reference/)  
> Официальный *Reactor Kafka Reference Guide* версии **1.3.25** (авторы: Rajini Sivaram, Mark Pollack, Oleh Dokuka, Gary Russell; last updated 2025-11-06 15:37:03 UTC / 18:37 MSK).  
> Это полный перевод прозы на русский плюс пояснения операторов и ключей конфигурации. Код скопирован дословно. Технические термины (Reactor, Kafka, Flux, Mono, backpressure, topic, partition, offset, sender, receiver и т.д.) оставлены на английском.

# Reactor Kafka Reference Guide (Справочное руководство Reactor Kafka)

Rajini Sivaram, Mark Pollack, Oleh Dokuka, Gary Russell

1.3.25

## Table of contents (Содержание)

- [Introduction (Введение)](#introduction-введение)
  - [1. Overview (Обзор)](#1-overview-обзор)
    - [1.1. Apache Kafka](#11-apache-kafka)
    - [1.2. Project Reactor](#12-project-reactor)
    - [1.3. Reactive API for Kafka (Reactive API для Kafka)](#13-reactive-api-for-kafka-reactive-api-для-kafka)
  - [2. Motivation (Мотивация)](#2-motivation-мотивация)
    - [2.1. Functional interface for Kafka (Функциональный интерфейс для Kafka)](#21-functional-interface-for-kafka-функциональный-интерфейс-для-kafka)
    - [2.2. Non-blocking Back-pressure (Неблокирующий back-pressure)](#22-non-blocking-back-pressure-неблокирующий-back-pressure)
    - [2.3. End-to-end Reactive Pipeline (Сквозной reactive pipeline)](#23-end-to-end-reactive-pipeline-сквозной-reactive-pipeline)
    - [2.4. Comparisons with other Kafka APIs (Сравнение с другими Kafka API)](#24-comparisons-with-other-kafka-apis-сравнение-с-другими-kafka-api)
      - [2.4.1. Kafka Producer and Consumer APIs](#241-kafka-producer-and-consumer-apis)
      - [2.4.2. Kafka Connect API](#242-kafka-connect-api)
      - [2.4.3. Kafka Streams API](#243-kafka-streams-api)
  - [3. Getting Started (Начало работы)](#3-getting-started-начало-работы)
    - [3.1. Requirements (Требования)](#31-requirements-требования)
    - [3.2. Quick Start](#32-quick-start)
      - [3.2.1. Start Kafka (Запуск Kafka)](#321-start-kafka-запуск-kafka)
      - [3.2.2. Run Reactor Kafka Samples (Запуск примеров)](#322-run-reactor-kafka-samples-запуск-примеров)
      - [3.2.3. Building Reactor Kafka Applications (Сборка приложений)](#323-building-reactor-kafka-applications-сборка-приложений)
  - [4. Additional Resources (Дополнительные материалы)](#4-additional-resources-дополнительные-материалы)
    - [4.1. Getting help (Помощь)](#41-getting-help-помощь)
    - [4.2. Resources (Ресурсы)](#42-resources-ресурсы)
- [Reference Documentation (Справочная документация)](#reference-documentation-справочная-документация)
  - [5. Reactor Kafka API](#5-reactor-kafka-api)
    - [5.1. Overview (Обзор)](#51-overview-обзор)
    - [5.2. Reactive Kafka Sender](#52-reactive-kafka-sender)
      - [5.2.1. Error handling (Обработка ошибок)](#521-error-handling-обработка-ошибок)
      - [5.2.2. Send without result metadata (Отправка без metadata результата)](#522-send-without-result-metadata-отправка-без-metadata-результата)
      - [5.2.3. Threading model (Модель потоков)](#523-threading-model-модель-потоков)
      - [5.2.4. Non-blocking back-pressure](#524-non-blocking-back-pressure)
      - [5.2.5. Closing the KafkaSender (Закрытие KafkaSender)](#525-closing-the-kafkasender-закрытие-kafkasender)
      - [5.2.6. Access to the underlying KafkaProducer (Доступ к KafkaProducer)](#526-access-to-the-underlying-kafkaproducer-доступ-к-kafkaproducer)
    - [5.3. Reactive Kafka Receiver](#53-reactive-kafka-receiver)
      - [5.3.1. Error handling (Обработка ошибок)](#531-error-handling-обработка-ошибок)
      - [5.3.2. Subscribing to wildcard patterns (Подписка по шаблону)](#532-subscribing-to-wildcard-patterns-подписка-по-шаблону)
      - [5.3.3. Manual assignment of topic partitions (Ручное assignment)](#533-manual-assignment-of-topic-partitions-ручное-assignment)
      - [5.3.4. Controlling commit frequency (Частота commit)](#534-controlling-commit-frequency-частота-commit)
      - [5.3.5. Out of Order Commits (Commit вне порядка)](#535-out-of-order-commits-commit-вне-порядка)
      - [5.3.6. Auto-acknowledgement of batches of records](#536-auto-acknowledgement-of-batches-of-records)
      - [5.3.7. Manual acknowledgement of batches of records](#537-manual-acknowledgement-of-batches-of-records)
      - [5.3.8. Disabling automatic commits (Отключение automatic commits)](#538-disabling-automatic-commits-отключение-automatic-commits)
      - [5.3.9. At-most-once delivery](#539-at-most-once-delivery)
      - [5.3.10. Partition assignment and revocation listeners](#5310-partition-assignment-and-revocation-listeners)
      - [5.3.11. Controlling start offsets for consuming records](#5311-controlling-start-offsets-for-consuming-records)
      - [5.3.12. Consumer lifecycle (Жизненный цикл consumer)](#5312-consumer-lifecycle-жизненный-цикл-consumer)
    - [5.4. Micrometer Metrics](#54-micrometer-metrics)
    - [5.5. Micrometer Observation](#55-micrometer-observation)
  - [6. Sample Scenarios (Типовые сценарии)](#6-sample-scenarios-типовые-сценарии)
    - [6.1. Sending records to Kafka](#61-sending-records-to-kafka)
    - [6.2. Replaying records from Kafka topics](#62-replaying-records-from-kafka-topics)
    - [6.3. Reactive pipeline with Kafka sink](#63-reactive-pipeline-with-kafka-sink)
    - [6.4. Reactive pipeline with Kafka source](#64-reactive-pipeline-with-kafka-source)
    - [6.5. Reactive pipeline with Kafka source and sink](#65-reactive-pipeline-with-kafka-source-and-sink)
    - [6.6. At-most-once delivery](#66-at-most-once-delivery)
    - [6.7. Fan-out with Multiple Streams](#67-fan-out-with-multiple-streams)
    - [6.8. Concurrent Processing with Partition-Based Ordering](#68-concurrent-processing-with-partition-based-ordering)
    - [6.9. Transactional send](#69-transactional-send)
    - [6.10. Exactly-once delivery](#610-exactly-once-delivery)

# Introduction (Введение)

## 1. Overview (Обзор)

### 1.1. Apache Kafka

[Kafka](https://kafka.apache.org) — масштабируемый высокопроизводительный распределённый messaging engine. Низкая latency, высокая throughput и fault-tolerance сделали Kafka популярным messaging-сервисом и мощной streaming-платформой для обработки real-time потоков events.

Apache Kafka предоставляет три основных API:

- Producer/Consumer API — публикация сообщений в Kafka topics и потребление сообщений из Kafka topics
- Connector API — вытягивание данных из существующих хранилищ в Kafka или выталкивание данных из Kafka topics в другие системы
- Streams API — преобразование и анализ real-time потоков events, опубликованных в Kafka

### 1.2. Project Reactor

[Reactor](https://projectreactor.io) — сильно оптимизированная reactive-библиотека для эффективных неблокирующих приложений на JVM на основе [Reactive Streams Specification](https://github.com/reactive-streams/reactive-streams-jvm). Приложения на Reactor выдерживают очень высокие message rates и работают с малым memory footprint; это подходит для event-driven приложений в архитектуре microservices.

Reactor реализует два publisher: [Flux&lt;T&gt;](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html) и [Mono&lt;T&gt;](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html); оба поддерживают неблокирующий back-pressure. Это позволяет обмениваться данными между потоками с предсказуемым использованием памяти, без лишней промежуточной буферизации и без блокировок.

### 1.3. Reactive API for Kafka (Reactive API для Kafka)

[Reactor Kafka](https://projectreactor.io/docs/kafka/release/api/index.html) — reactive API для Kafka на основе Reactor и Kafka Producer/Consumer API. Reactor Kafka API позволяет публиковать сообщения в Kafka и потреблять их из Kafka через функциональные API с неблокирующим back-pressure и очень малыми накладными расходами. Приложения на Reactor могут использовать Kafka как message bus или streaming-платформу и строить сквозной reactive pipeline вместе с другими системами.

## 2. Motivation (Мотивация)

### 2.1. Functional interface for Kafka (Функциональный интерфейс для Kafka)

Reactor Kafka — функциональный Java API для Kafka. Для приложений в функциональном стиле этот API позволяет встроить взаимодействие с Kafka без включения нефункциональных асинхронных produce/consume API в прикладную логику.

### 2.2. Non-blocking Back-pressure (Неблокирующий back-pressure)

Reactor Kafka API использует неблокирующий back-pressure Reactor. Например, в pipeline, где сообщения из внешнего источника (HTTP proxy) публикуются в Kafka, back-pressure легко накладывается на весь pipeline, ограничивая число in-flight сообщений и контролируя память. Сообщения идут по pipeline по мере доступности; Reactor ограничивает скорость потока, чтобы избежать overflow, оставляя прикладную логику простой.

### 2.3. End-to-end Reactive Pipeline (Сквозной reactive pipeline)

Ценность Reactor Kafka — эффективное использование ресурсов в приложениях с несколькими внешними взаимодействиями, где Kafka — одна из внешних систем. Сквозные reactive pipelines выигрывают от неблокирующего back-pressure и эффективного использования потоков, обрабатывая большое число concurrent запросов. Оптимизации Project Reactor дают низкие накладные расходы и предсказуемое capacity planning для pipeline с низкой latency и высокой throughput.

### 2.4. Comparisons with other Kafka APIs (Сравнение с другими Kafka API)

Reactor Kafka не заменяет существующие Kafka API. Это альтернативный API для reactive event-driven приложений.

#### 2.4.1. Kafka Producer and Consumer APIs

Для не-reactive приложений Kafka Producer/Consumer API даёт интерфейс с низкой latency для публикации и потребления сообщений.

Приложения, использующие Kafka как message bus через этот API, могут рассмотреть переход на Reactor Kafka, если приложение написано в функциональном стиле.

#### 2.4.2. Kafka Connect API

[Kafka Connect](https://kafka.apache.org/documentation#connect) даёт простой интерфейс миграции сообщений из внешней системы данных (например, database) в один или несколько Kafka topics. Существующие connectors позволяют сделать это без нового кода.

Приложения на Connector API могут рассмотреть Reactor Kafka, если для внешней системы есть reactive API и нужны transformations. Когда transformations включают другой I/O (например, дополнительные данные из другой database), reactive pipeline получает сквозной неблокирующий back-pressure. Сообщения из/в разные Kafka partitions можно обрабатывать параллельно, повышая throughput без блокировок на I/O. Pull-модель Reactor задаёт темп потока, эффективно используя потоки и память без overflow-обработки в приложении.

#### 2.4.3. Kafka Streams API

[Kafka Streams](https://kafka.apache.org/documentation#streams) даёт лёгкие API для stream processing приложений, которые обрабатывают данные в Kafka стандартными streaming-концепциями и примитивами преобразования. Простая модель потоков снимает необходимость в back-pressure. Это хорошо, когда transformations не требуют внешних взаимодействий.

Reactor Kafka полезен для streams-приложений, которые читают Kafka и для transformations ходят во внешние системы (например, database). Тогда Reactor даёт сквозной неблокирующий back-pressure и лучшее использование ресурсов, если все внешние взаимодействия тоже reactive.

## 3. Getting Started (Начало работы)

### 3.1. Requirements (Требования)

Нужен установленный Java JRE (Java 8 или новее).

Нужен установленный [Apache Kafka](https://kafka.apache.org) (1.0.0 или новее). Kafka можно скачать с [kafka.apache.org/downloads.html](https://kafka.apache.org/downloads.html). Клиентская библиотека Apache Kafka для Reactor Kafka должна быть 2.0.0 или новее, версия broker — 1.0.0 или выше.

### 3.2. Quick Start

Этот quick start поднимает одноузловые Zookeeper и Kafka и запускает sample reactive producer и consumer. Инструкции по multi-broker cluster: [здесь](https://kafka.apache.org/documentation#quickstart_multibroker).

#### 3.2.1. Start Kafka (Запуск Kafka)

Если Kafka ещё не скачан, скачайте версию [2.0.0](https://www.apache.org/dyn/closer.cgi?path=/kafka/2.0.0/kafka_2.11-2.0.0.tgz) или выше.

Распакуйте релиз и задайте `KAFKA_DIR` на каталог установки. Например:

```bash
> tar -zxf kafka_2.11-2.0.0.tgz -C /opt
> export KAFKA_DIR=/opt/kafka_2.11-2.0.0
```

**Разбор операторов и ключей**

- `tar -zxf` — распаковка gzip-архива Kafka.
- `export KAFKA_DIR` — переменная окружения с путём к установке Kafka (далее используется в shell-скриптах).

Запуск одноузлового Zookeeper из поставки Kafka:

```bash
> $KAFKA_DIR/bin/zookeeper-server-start.sh $KAFKA_DIR/config/zookeeper.properties > /tmp/zookeeper.log &
```

**Разбор операторов и ключей**

- `zookeeper-server-start.sh` — стартовый скрипт Zookeeper.
- `zookeeper.properties` — конфигурация Zookeeper (порт, dataDir и т.д.).
- `&` — фоновый процесс; stdout перенаправляется в лог.

Запуск одноузлового Kafka:

```bash
> $KAFKA_DIR/bin/kafka-server-start.sh $KAFKA_DIR/config/server.properties > /tmp/kafka.log &
```

**Разбор операторов и ключей**

- `kafka-server-start.sh` — старт broker.
- `server.properties` — конфигурация broker (`listeners`, `log.dirs`, связь с Zookeeper и т.д.).

Создание Kafka topic:

```bash
> $KAFKA_DIR/bin/kafka-topics.sh --zookeeper localhost:2181 --create --replication-factor 1 --partitions 2 --topic demo-topic
Created topic "demo-topic".
```

**Разбор операторов и ключей**

- `kafka-topics.sh --create` — создание topic.
- `--zookeeper localhost:2181` — адрес Zookeeper (в новых версиях Kafka чаще `--bootstrap-server`).
- `--replication-factor 1` — одна replica (достаточно для single-node).
- `--partitions 2` — две partitions.
- `--topic demo-topic` — имя topic.

Проверка, что topic создан:

```bash
> $KAFKA_DIR/bin/kafka-topics.sh --zookeeper localhost:2181 --describe
Topic: demo-topic        PartitionCount:2                ReplicationFactor:1        Configs:
Topic: demo-topic        Partition: 0        Leader: 0        Replicas: 0                Isr: 0
Topic: demo-topic        Partition: 1        Leader: 0        Replicas: 0                Isr: 0
```

**Разбор операторов и ключей**

- `--describe` — описание topic: `PartitionCount`, `ReplicationFactor`, `Leader`, `Replicas`, `Isr` (in-sync replicas).

#### 3.2.2. Run Reactor Kafka Samples (Запуск примеров)

Скачайте и соберите Reactor Kafka с [github.com/reactor/reactor-kafka/](https://github.com/reactor/reactor-kafka/).

```bash
> git clone https://github.com/reactor/reactor-kafka
> cd reactor-kafka
> ./gradlew jar
```

**Разбор операторов и ключей**

- `git clone` — копия репозитория.
- `./gradlew jar` — Gradle wrapper, задача `jar`: сборка артефакта без публикации.

Задайте `CLASSPATH` для samples. Его даёт задача `classpath` подпроекта samples:

```bash
> export CLASSPATH=`./gradlew -q :reactor-kafka-samples:classpath`
```

**Разбор операторов и ключей**

- `./gradlew -q :reactor-kafka-samples:classpath` — тихий (`-q`) вывод classpath модуля samples.
- `CLASSPATH` — путь, по которому `kafka-run-class.sh` найдёт классы sample.

##### Sample Producer (Пример Producer)

Код: [SampleProducer.java](https://github.com/reactor/reactor-kafka/blob/main/reactor-kafka-samples/src/main/java/reactor/kafka/samples/SampleProducer.java).

```bash
> $KAFKA_DIR/bin/kafka-run-class.sh reactor.kafka.samples.SampleProducer
Message 2 sent successfully, topic-partition=demo-topic-1 offset=0 timestamp=13:33:16:716 GMT 30 Nov 2016
Message 3 sent successfully, topic-partition=demo-topic-1 offset=1 timestamp=13:33:16:716 GMT 30 Nov 2016
Message 4 sent successfully, topic-partition=demo-topic-1 offset=2 timestamp=13:33:16:716 GMT 30 Nov 2016
Message 6 sent successfully, topic-partition=demo-topic-1 offset=3 timestamp=13:33:16:716 GMT 30 Nov 2016
Message 7 sent successfully, topic-partition=demo-topic-1 offset=4 timestamp=13:33:16:716 GMT 30 Nov 2016
Message 10 sent successfully, topic-partition=demo-topic-1 offset=5 timestamp=13:33:16:716 GMT 30 Nov 2016
Message 11 sent successfully, topic-partition=demo-topic-1 offset=6 timestamp=13:33:16:716 GMT 30 Nov 2016
Message 12 sent successfully, topic-partition=demo-topic-1 offset=7 timestamp=13:33:16:717 GMT 30 Nov 2016
Message 13 sent successfully, topic-partition=demo-topic-1 offset=8 timestamp=13:33:16:717 GMT 30 Nov 2016
Message 14 sent successfully, topic-partition=demo-topic-1 offset=9 timestamp=13:33:16:717 GMT 30 Nov 2016
Message 16 sent successfully, topic-partition=demo-topic-1 offset=10 timestamp=13:33:16:717 GMT 30 Nov 2016
Message 17 sent successfully, topic-partition=demo-topic-1 offset=11 timestamp=13:33:16:717 GMT 30 Nov 2016
Message 20 sent successfully, topic-partition=demo-topic-1 offset=12 timestamp=13:33:16:717 GMT 30 Nov 2016
Message 1 sent successfully, topic-partition=demo-topic-0 offset=0 timestamp=13:33:16:712 GMT 30 Nov 2016
Message 5 sent successfully, topic-partition=demo-topic-0 offset=1 timestamp=13:33:16:716 GMT 30 Nov 2016
Message 8 sent successfully, topic-partition=demo-topic-0 offset=2 timestamp=13:33:16:716 GMT 30 Nov 2016
Message 9 sent successfully, topic-partition=demo-topic-0 offset=3 timestamp=13:33:16:716 GMT 30 Nov 2016
Message 15 sent successfully, topic-partition=demo-topic-0 offset=4 timestamp=13:33:16:717 GMT 30 Nov 2016
Message 18 sent successfully, topic-partition=demo-topic-0 offset=5 timestamp=13:33:16:717 GMT 30 Nov 2016
Message 19 sent successfully, topic-partition=demo-topic-0 offset=6 timestamp=13:33:16:717 GMT 30 Nov 2016
```

**Разбор операторов и ключей**

- `kafka-run-class.sh` — запуск класса с Kafka classpath.
- `reactor.kafka.samples.SampleProducer` — sample producer.
- В выводе: `topic-partition` (topic + partition), `offset`, `timestamp` — metadata успешного send.

Sample producer отправляет 20 сообщений в topic `demo-topic` через default partitioner. Partition и offset каждого опубликованного сообщения печатаются в console. Порядок результатов может отличаться от порядка публикации. Результаты упорядочены внутри каждой partition, но результаты разных partitions могут чередоваться. Индекс сообщения используется как correlation metadata, чтобы сопоставить результат с сообщением.

##### Sample Consumer (Пример Consumer)

Код: [SampleConsumer.java](https://github.com/reactor/reactor-kafka/blob/main/reactor-kafka-samples/src/main/java/reactor/kafka/samples/SampleConsumer.java).

```bash
> $KAFKA_DIR/bin/kafka-run-class.sh reactor.kafka.samples.SampleConsumer
Received message: topic-partition=demo-topic-1 offset=0 timestamp=13:33:16:716 GMT 30 Nov 2016 key=2 value=Message_2
Received message: topic-partition=demo-topic-1 offset=1 timestamp=13:33:16:716 GMT 30 Nov 2016 key=3 value=Message_3
Received message: topic-partition=demo-topic-1 offset=2 timestamp=13:33:16:716 GMT 30 Nov 2016 key=4 value=Message_4
Received message: topic-partition=demo-topic-1 offset=3 timestamp=13:33:16:716 GMT 30 Nov 2016 key=6 value=Message_6
Received message: topic-partition=demo-topic-1 offset=4 timestamp=13:33:16:716 GMT 30 Nov 2016 key=7 value=Message_7
Received message: topic-partition=demo-topic-1 offset=5 timestamp=13:33:16:716 GMT 30 Nov 2016 key=10 value=Message_10
Received message: topic-partition=demo-topic-1 offset=6 timestamp=13:33:16:716 GMT 30 Nov 2016 key=11 value=Message_11
Received message: topic-partition=demo-topic-1 offset=7 timestamp=13:33:16:717 GMT 30 Nov 2016 key=12 value=Message_12
Received message: topic-partition=demo-topic-1 offset=8 timestamp=13:33:16:717 GMT 30 Nov 2016 key=13 value=Message_13
Received message: topic-partition=demo-topic-1 offset=9 timestamp=13:33:16:717 GMT 30 Nov 2016 key=14 value=Message_14
Received message: topic-partition=demo-topic-1 offset=10 timestamp=13:33:16:717 GMT 30 Nov 2016 key=16 value=Message_16
Received message: topic-partition=demo-topic-1 offset=11 timestamp=13:33:16:717 GMT 30 Nov 2016 key=17 value=Message_17
Received message: topic-partition=demo-topic-1 offset=12 timestamp=13:33:16:717 GMT 30 Nov 2016 key=20 value=Message_20
Received message: topic-partition=demo-topic-0 offset=0 timestamp=13:33:16:712 GMT 30 Nov 2016 key=1 value=Message_1
Received message: topic-partition=demo-topic-0 offset=1 timestamp=13:33:16:716 GMT 30 Nov 2016 key=5 value=Message_5
Received message: topic-partition=demo-topic-0 offset=2 timestamp=13:33:16:716 GMT 30 Nov 2016 key=8 value=Message_8
Received message: topic-partition=demo-topic-0 offset=3 timestamp=13:33:16:716 GMT 30 Nov 2016 key=9 value=Message_9
Received message: topic-partition=demo-topic-0 offset=4 timestamp=13:33:16:717 GMT 30 Nov 2016 key=15 value=Message_15
Received message: topic-partition=demo-topic-0 offset=5 timestamp=13:33:16:717 GMT 30 Nov 2016 key=18 value=Message_18
Received message: topic-partition=demo-topic-0 offset=6 timestamp=13:33:16:717 GMT 30 Nov 2016 key=19 value=Message_19
```

**Разбор операторов и ключей**

- `reactor.kafka.samples.SampleConsumer` — sample consumer.
- Поля вывода: `topic-partition`, `offset`, `timestamp`, `key`, `value` — стандартные поля consumer record.

Sample consumer читает topic `demo-topic` и печатает сообщения. 20 сообщений producer должны появиться в console. Сообщения упорядочены внутри partition; между partitions порядок может чередоваться.

#### 3.2.3. Building Reactor Kafka Applications (Сборка приложений)

Чтобы собрать своё приложение на Reactor Kafka API, добавьте зависимость.

Для Gradle:

```groovy
dependencies {
    compile "io.projectreactor.kafka:reactor-kafka:1.3.25"
}
```

**Разбор операторов и ключей**

- `dependencies { compile ... }` — Gradle Groovy DSL: compile-зависимость (в современных Gradle — `implementation`).
- `io.projectreactor.kafka:reactor-kafka:1.3.25` — `groupId`:`artifactId`:`version` артефакта Reactor Kafka.

Для Maven:

```xml
<dependency>
    <groupId>io.projectreactor.kafka</groupId>
    <artifactId>reactor-kafka</artifactId>
    <version>1.3.25</version>
</dependency>
```

**Разбор операторов и ключей**

- `groupId` / `artifactId` / `version` — координаты Maven той же библиотеки `reactor-kafka` 1.3.25.

## 4. Additional Resources (Дополнительные материалы)

### 4.1. Getting help (Помощь)

Если возникли проблемы с Reactor Kafka, можно обратиться за помощью.

Баги: [github.com/reactor/reactor-kafka/issues](https://github.com/reactor/reactor-kafka/issues).

Reactor Kafka — open source; код и документация: [github.com/reactor/reactor-kafka](https://github.com/reactor/reactor-kafka).

### 4.2. Resources (Ресурсы)

- [Reactor Kafka on github](https://github.com/reactor/reactor-kafka)
- [Apache Kafka](https://kafka.apache.org/documentation.html)
- [Project Reactor](https://projectreactor.io/)
- [Reactor Core](https://github.com/reactor/reactor-core)
- [Reactive Streams Specification](https://github.com/reactive-streams/reactive-streams-jvm)
- [Understanding Reactive types](https://spring.io/blog/2016/04/19/understanding-reactive-types)
- [Lite Rx API Hands-on](https://github.com/reactor/lite-rx-api-hands-on)
- [Reactor by Example](https://www.infoq.com/articles/reactor-by-example)


# Reference Documentation (Справочная документация)

## 5. Reactor Kafka API

### 5.1. Overview (Обзор)

Этот раздел описывает reactive API для produce и consume сообщений через Apache Kafka. Два главных интерфейса Reactor Kafka:

1. `reactor.kafka.sender.KafkaSender` — публикация сообщений в Kafka
2. `reactor.kafka.receiver.KafkaReceiver` — потребление сообщений из Kafka

Полный API: [javadocs](https://projectreactor.io/docs/kafka/release/api/index.html).

Проект использует [Reactor Core](https://github.com/reactor/reactor-core), чтобы выставить ["Reactive Streams"](https://github.com/reactive-streams/reactive-streams-jvm) API.

### 5.2. Reactive Kafka Sender

Исходящие сообщения отправляются через `reactor.kafka.sender.KafkaSender`. Sender thread-safe и может разделяться между потоками для повышения throughput. Один `KafkaSender` связан с одним `KafkaProducer`, который доставляет сообщения в Kafka.

`KafkaSender` создаётся с экземпляром опций `reactor.kafka.sender.SenderOptions`. Изменения `SenderOptions` после создания `KafkaSender` не применяются. Свойства `SenderOptions` (список bootstrap Kafka brokers, serializers) передаются в подлежащий `KafkaProducer`. Их задают при создании или сеттером `SenderOptions#producerProperty`. Другие опции reactive KafkaSender (максимум in-flight сообщений) тоже настраивают до создания экземпляра.

Generic-типы `SenderOptions<K, V>` и `KafkaSender<K, V>` — типы key и value producer records; соответствующие serializers должны быть заданы на `SenderOptions` до создания `KafkaSender`.

```java
Map<String, Object> producerProps = new HashMap<>();
producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class);
producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

SenderOptions<Integer, String> senderOptions =
    SenderOptions.<Integer, String>create(producerProps)       // (1)
                 .maxInFlight(1024);                           // (2)
```

1. Задать свойства подлежащего `KafkaProducer`.
2. Настроить опции reactive KafkaSender.

**Разбор операторов и ключей**

- `ProducerConfig.BOOTSTRAP_SERVERS_CONFIG` (`bootstrap.servers`) — список broker для начального подключения.
- `ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG` — serializer ключа (`IntegerSerializer`).
- `ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG` — serializer значения (`StringSerializer`).
- `SenderOptions.create(producerProps)` — фабрика опций sender из map свойств Producer.
- `maxInFlight(1024)` — максимум одновременных незавершённых send (backpressure вверх по stream).

После настройки создаётся `KafkaSender`:

```java
KafkaSender<Integer, String> sender = KafkaSender.create(senderOptions);
```

**Разбор операторов и ключей**

- `KafkaSender.create` — создаёт thread-safe sender поверх одного `KafkaProducer`. `KafkaProducer` создаётся лениво при первой отправке.

`KafkaSender` готов к отправке. Подлежащий `KafkaProducer` создаётся лениво, когда первое сообщение готово к send. Соединений с Kafka ещё нет.

Каждое исходящее сообщение — `SenderRecord`. Это Kafka [ProducerRecord](https://kafka.apache.org/0102/javadoc/org/apache/kafka/clients/producer/ProducerRecord.html) плюс correlation metadata для сопоставления результатов с records. `ProducerRecord` содержит пару key/value и имя Kafka topic. Опционально — partition (или выбор через configured partitioner) и timestamp (иначе Producer ставит текущий). Correlation metadata в Kafka не уходит, но попадает в `SendResult` при успехе или ошибке. Результаты send в разные partitions могут чередоваться; metadata позволяет сопоставить результат с record.

Для отправки создаётся [Flux&lt;SenderRecord&gt;](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html). Для начинающих: [Lite Rx API Hands-on](https://github.com/reactor/lite-rx-api-hands-on).

```java
Flux<SenderRecord<Integer, String, Integer>> outboundFlux =
    Flux.range(1, 10)
        .map(i -> SenderRecord.create(topic, partition, timestamp, i, "Message_" + i, i));
```

**Разбор операторов и ключей**

- `Flux.range(1, 10)` — publisher десяти целых 1..10.
- `map` — преобразование элемента в `SenderRecord`.
- `SenderRecord.create(topic, partition, timestamp, key, value, correlationMetadata)` — record: topic, partition, timestamp, key=`i`, value=`"Message_"+i`, correlation metadata=`i`.

Код выше строит последовательность сообщений; индекс — correlation metadata. Исходящий Flux отправляется через ранее созданный `KafkaSender`.

Ниже records уходят в Kafka; печатаются response metadata от Kafka и correlation metadata. Финальный `subscribe()` запрашивает upstream отправить records; metadata течёт downstream. Обработчик `onNext` печатает record metadata и correlation metadata. Ответ Kafka содержит partition и offset (если доступен). При нескольких partitions ответы упорядочены внутри partition и могут чередоваться между partitions.

```java
sender.send(outboundFlux)                          // (1)
      .doOnError(e-> log.error("Send failed", e))  // (2)
      .doOnNext(r -> System.out.printf("Message #%d send response: %s\n", r.correlationMetadata(), r.recordMetadata())) // (3)
      .subscribe();    // (4)
```

1. Reactive send исходящего Flux.
2. При ошибке Kafka send — записать error.
3. Печать metadata Kafka и индекса из `correlationMetadata()`.
4. Subscribe запускает поток records из `outboundFlux` в Kafka.

**Разбор операторов и ключей**

- `KafkaSender.send` — принимает `Publisher<SenderRecord>` и возвращает `Flux<SenderResult>`.
- `doOnError` — side-effect на error signal.
- `doOnNext` — side-effect на каждый `SenderResult`.
- `correlationMetadata()` — прикладной идентификатор record.
- `recordMetadata()` — Kafka `RecordMetadata` (topic, partition, offset).
- `subscribe()` — подписка subscriber, без неё цепочка не стартует.

Полный listing sample producer: [SampleProducer.java](https://github.com/reactor/reactor-kafka/blob/main/reactor-kafka-samples/src/main/java/reactor/kafka/samples/SampleProducer.java).

#### 5.2.1. Error handling (Обработка ошибок)

```java
public SenderOptions<K, V> stopOnError(boolean stopOnError);
```

**Разбор операторов и ключей**

- `SenderOptions#stopOnError()` — прерывать ли последовательность send сразу после неудачной доставки record (после retries) или ждать обработки всех records. Сочетается с `ProducerConfig#ACKS_CONFIG` и `ProducerConfig#RETRIES_CONFIG` для нужного quality of service.

```java
<T> Flux<SenderResult<T>> send(Publisher<SenderRecord<K, V, T>> outboundRecords);
```

**Разбор операторов и ключей**

- `send(Publisher<SenderRecord>)` — reactive send; элемент результата — `SenderResult<T>`.

Если `stopOnError` = false, на каждый исходящий record приходит success или error response. Для ошибок исключение Kafka кладётся в `SenderResult` и читается через `SenderResult#exception()`. Flux завершается ошибкой после попытки отправить все records из `outboundRecords`. Если `outboundRecords` — нетерминирующий `Flux`, send продолжается, пока result `Flux` явно не отменят.

Если `stopOnError` = true, возвращается ответ на первый неудачный send и result Flux сразу завершается ошибкой. Несколько исходящих сообщений могут быть in-flight: часть может успешно дойти после первой ошибки. `SenderOptions#maxInFlight()` ограничивает число in-flight сообщений.

#### 5.2.2. Send without result metadata (Отправка без metadata результата)

Если индивидуальные результаты не нужны, `ProducerRecord` можно отправить без correlation metadata через `KafkaOutbound`. Это fluent-интерфейс для цепочки send.

```java
KafkaOutbound<K, V> send(Publisher<? extends ProducerRecord<K, V>> outboundRecords);
```

**Разбор операторов и ключей**

- `KafkaOutbound.send` — send `ProducerRecord` без обёртки `SenderRecord`.

Последовательность стартует подпиской на Mono из `KafkaOutbound#then()`. Mono успешно завершается, если все outbound records доставлены. Mono терминируется на первой ошибке send. Если `outboundRecords` нетерминирующий Flux, records продолжают уходить, пока send не упадёт или Mono не отменят.

```java
sender.createOutbound()
      .send(Flux.range(1,  10)
                .map(i -> new ProducerRecord<Integer, String>(topic, i, "Message_" + i))) // (1)
      .then()                                                    // (2)
      .doOnError(e -> e.printStackTrace())                       // (3)
      .doOnSuccess(s -> System.out.println("Sends succeeded"))   // (4)
      .subscribe();                                              // (5)
```

1. Flux `ProducerRecord`; records не обёрнуты в `SenderRecord`.
2. Mono для подписки и старта потока.
3. Error — не удалось отправить один или несколько records.
4. Success — все records опубликованы; отдельные partitions/offsets не возвращаются.
5. Subscribe запрашивает фактические send.

**Разбор операторов и ключей**

- `KafkaSender.createOutbound()` — fluent `KafkaOutbound`.
- `Flux.range` / `map` — генерация `ProducerRecord(topic, key, value)`.
- `then()` — Mono завершения всей цепочки send.
- `doOnError` / `doOnSuccess` — side-effects терминальных сигналов Mono.
- `subscribe()` — запуск.

Несколько send можно сцепить на `KafkaOutbound`. При подписке на Mono из `KafkaOutbound#then()` send вызываются в порядке объявления. Цепочка отменяется, если любой send падает после настроенных retries.

```java
sender.createOutbound()
      .send(flux1)                                               // (1)
      .send(flux2)
      .send(flux3)
      .then()                                                    // (2)
      .doOnError(e -> e.printStackTrace())                       // (3)
      .doOnSuccess(s -> System.out.println("Sends succeeded"))   // (4)
      .subscribe();                                              // (5)
```

1. Отправка `flux1`, `flux2`, `flux3` по порядку.
2. Mono старта последовательности.
3. Error — ошибка в любом send цепочки.
4. Success — успешный send всех records цепочки.
5. Subscribe инициирует последовательность.

**Разбор операторов и ключей**

- Повторные `KafkaOutbound.send` — последовательная цепочка publisher.
- `then()` — одно завершение на всю цепочку.
- retries `KafkaProducer` выполняются всегда; ошибка reactive `KafkaSender` означает неудачу после всех retry. Retries могут нарушить порядок. `ProducerConfig#MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION` = 1 предотвращает переупорядочивание.

#### 5.2.3. Threading model (Модель потоков)

`KafkaProducer` использует отдельный network thread для запросов и ответов. Чтобы этот поток не блокировался приложением при обработке результатов, `KafkaSender` доставляет ответы на отдельный scheduler. По умолчанию это однопоточный pooled scheduler, освобождаемый, когда не нужен. Scheduler можно заменить (например, parallel scheduler, если send — часть большего pipeline) на `SenderOptions` до создания KafkaSender:

```java
public SenderOptions<K, V> scheduler(Scheduler scheduler);
```

**Разбор операторов и ключей**

- `SenderOptions.scheduler(Scheduler)` — scheduler, на котором приложению доставляются send results (не network thread Producer).

#### 5.2.4. Non-blocking back-pressure

Число in-flight send контролирует опция `maxInFlight`. Запросы новых элементов из upstream ограничены этим значением, чтобы число запросов с pending responses было ограничено. Вместе с `buffer.memory` и `max.block.ms` у `KafkaProducer` это управляет памятью и потоками в reactive pipeline. Настраивается на `SenderOptions` до создания KafkaSender. Default = 256. Для маленьких сообщений большее значение повышает throughput.

```java
public SenderOptions<K, V> maxInFlight(int maxInFlight);
```

**Разбор операторов и ключей**

- `maxInFlight` — лимит in-flight send.
- `buffer.memory` — память буфера Producer.
- `max.block.ms` — сколько Producer может блокироваться при полном буфере.

#### 5.2.5. Closing the KafkaSender (Закрытие KafkaSender)

Когда sender больше не нужен, экземпляр закрывают. Закрывается `KafkaProducer`, клиентские соединения и память producer.

```java
sender.close();
```

**Разбор операторов и ключей**

- `KafkaSender.close()` — закрытие sender и подлежащего `KafkaProducer`.

#### 5.2.6. Access to the underlying KafkaProducer (Доступ к KafkaProducer)

Иногда нужен доступ к подлежащему producer для действий вне `KafkaSender` (например, число partitions topic, чтобы выбрать partition). Операции вроде не-`send` выполняют на `KafkaProducer` через `KafkaSender#doOnProducer`.

```java
sender.doOnProducer(producer -> producer.partitionsFor(topic))
      .doOnSuccess(partitions -> System.out.println("Partitions " + partitions))
      .subscribe();
```

**Разбор операторов и ключей**

- `doOnProducer` — асинхронно выполняет функцию на `KafkaProducer`; возвращает `Mono` со значением функции.
- `KafkaProducer.partitionsFor(topic)` — метаданные partitions topic.
- `doOnSuccess` — side-effect успешного Mono.
- `subscribe()` — запуск.

Пользовательские методы выполняются асинхронно. `Mono` из `doOnProducer` завершается значением функции.


### 5.3. Reactive Kafka Receiver

Сообщения из Kafka topics потребляет `reactor.kafka.receiver.KafkaReceiver`. Каждый `KafkaReceiver` связан с одним `KafkaConsumer`. `KafkaReceiver` не thread-safe: подлежащий `KafkaConsumer` нельзя использовать concurrently из нескольких потоков.

Receiver создаётся с `reactor.kafka.receiver.ReceiverOptions`. Изменения `ReceiverOptions` после создания receiver не используются. Свойства (bootstrap brokers, de-serializers) передаются в `KafkaConsumer`. Их задают при создании или через `ReceiverOptions#consumerProperty`. Прочие опции, включая subscription topics, нужно добавить до создания `KafkaReceiver`.

Generic-типы `ReceiverOptions<K, V>` и `KafkaReceiver<K, V>` — типы key/value consumer records; de-serializers задают на `ReceiverOptions` до создания receiver.

```java
Map<String, Object> consumerProps = new HashMap<>();
consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "sample-group");
consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, IntegerDeserializer.class);
consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

ReceiverOptions<Integer, String> receiverOptions =
    ReceiverOptions.<Integer, String>create(consumerProps)         // (1)
                   .subscription(Collections.singleton(topic));    // (2)
```

1. Свойства для `KafkaConsumer`.
2. Topics для subscription.

**Разбор операторов и ключей**

- `ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG` (`bootstrap.servers`) — список broker.
- `ConsumerConfig.GROUP_ID_CONFIG` (`group.id`) — идентификатор consumer group.
- `KEY_DESERIALIZER_CLASS_CONFIG` / `VALUE_DESERIALIZER_CLASS_CONFIG` — de-serializers key/value.
- `ReceiverOptions.create` — фабрика опций.
- `subscription(Collections.singleton(topic))` — subscribe на один topic (group management).

После настройки создают `KafkaReceiver` и inbound Flux. `KafkaConsumer` создаётся лениво при subscribe на inbound Flux.

```java
Flux<ReceiverRecord<Integer, String>> inboundFlux =
    KafkaReceiver.create(receiverOptions)
                 .receive();
```

**Разбор операторов и ключей**

- `KafkaReceiver.create` — экземпляр receiver.
- `receive()` — `Flux<ReceiverRecord>` с committable offset на каждом record.

Inbound Flux готов к consume. Каждый элемент — `ReceiverRecord`: [ConsumerRecord](https://kafka.apache.org/0102/javadoc/org/apache/kafka/clients/consumer/ConsumerRecord.html) плюс committable `ReceiverOffset`. Offset нужно acknowledge после обработки: неподтверждённые offsets не commit. При настроенных commit interval / commit batch size acknowledged offsets commit периодически. Ручной commit: `ReceiverOffset#commit()`.

```java
inboundFlux.subscribe(r -> {
    System.out.printf("Received message: %s\n", r);           // (1)
    r.receiverOffset().acknowledge();                         // (2)
});
```

1. Печать каждого consumer record.
2. Acknowledge: record обработан, offset можно commit.

**Разбор операторов и ключей**

- `subscribe` — подписка, создаёт consumer и начинает poll.
- `receiverOffset()` — `ReceiverOffset` данного record.
- `acknowledge()` — помечает offset (и предыдущие в той же partition) к периодическому commit.

#### 5.3.1. Error handling (Обработка ошибок)

В reactive streams error — терминальный сигнал: error в inbound Flux отменяет subscription и фактически останавливает consumer. Смягчение: оператор `retry()` (или `retryWhen` для тонкой настройки) — создаётся новый consumer:

```java
Flux<ReceiverRecord<Integer, String>> inboundFlux =
    KafkaReceiver.create(receiverOptions)
        .receive()
        .retryWhen(Retry.backoff(3, Duration.of(10L, ChronoUnit.SECONDS)));
```

**Разбор операторов и ключей**

- `receive()` — inbound Flux.
- `retryWhen(Retry.backoff(3, Duration.of(10L, ChronoUnit.SECONDS)))` — до 3 повторов с backoff 10 секунд; новый consumer.

Ошибки обработки events (не самого `KafkaConsumer`) нужно ловить ближе к источнику и не пускать в inbound Flux, чтобы consumer не перезапускался из-за прикладных ошибок.

#### 5.3.2. Subscribing to wildcard patterns (Подписка по шаблону)

Тот же API подписывается на несколько topics коллекцией в `ReceiverOptions#subscription()`. Можно задать wildcard pattern. Group management в `KafkaConsumer` динамически обновляет assignment при создании/удалении matching topics и назначает partitions доступным consumer instances.

```java
receiverOptions = receiverOptions.subscription(Pattern.compile("demo.*"));  // (1)
```

1. Consume из всех topics, имя которых начинается с `demo`.

**Разбор операторов и ключей**

- `subscription(Pattern.compile("demo.*"))` — subscribe по regex; заменяет предыдущую subscription.

Изменения `ReceiverOptions` — только до создания receiver. Новая subscription удаляет существующие на options.

#### 5.3.3. Manual assignment of topic partitions (Ручное assignment)

Partitions можно назначить вручную без consumer group management.

```java
receiverOptions = receiverOptions.assignment(Collections.singleton(new TopicPartition(topic, 0))); // (1)
```

1. Consume из partition 0 указанного topic.

**Разбор операторов и ключей**

- `assignment(...)` — ручное assignment; `TopicPartition(topic, 0)` — конкретная partition.
- Существующие subscription/assignment на options удаляются.

Каждый receiver с этим manual assignment читает все указанные partitions.

#### 5.3.4. Controlling commit frequency (Частота commit)

Частоту commit задают commit interval и commit batch size. Commit выполняется, когда срабатывает любой из порогов. Одно или оба задают на `ReceiverOptions` до создания receiver. При commit interval хотя бы один commit планируется в интервале, если были consumed records. При commit batch size commit планируется после заданного числа acknowledged records.

Ручной acknowledge после обработки плюс automatic commits по частоте дают at-least-once: сообщения повторно доставляются, если приложение упало после dispatch, но до обработки и acknowledge. Commit только для offsets, явно подтверждённых `ReceiverOffset#acknowledge()`. Acknowledge offset подтверждает все предыдущие offsets той же partition. Все acknowledged offsets commit при revocation partitions во время rebalance и при завершении receive Flux.

Для тонкого контроля периодические commits отключают и вызывают `ReceiverOffset#commit()`. По умолчанию commit асинхронный; `Mono#block()` даёт синхронный commit. Можно acknowledge по мере consume и периодически вызывать `commit()` для пакета.

```java
receiver.receive()
        .doOnNext(r -> {
                process(r);
                r.receiverOffset().commit().block();
            });
```

**Разбор операторов и ключей**

- `receive()` — Flux records.
- `doOnNext` — обработка каждого record.
- `receiverOffset().commit()` — Mono явного commit (acknowledge + commit предыдущих offsets partition).
- `block()` — синхронное ожидание commit (не на receiver thread без `publishOn`).

Commit offset подтверждает и commit все предыдущие offsets partition. Acknowledged offsets commit при revocation и при terminate receive Flux.

Начиная с 1.3.12, при rebalance из-за смены членов группы rebalance задерживается, пока не обработаны records предыдущего poll. Это `ReceiverOptions`: `maxDelayRebalance` (default 60s) и `commitIntervalDuringDelay` (default 100ms). Во время задержки доступные offsets commit каждые `commitIntervalDuringDelay` мс. `maxDelayRebalance` должен быть меньше `max.poll.interval.ms`, иначе forced rebalance из-за «неотвечающего» consumer.

#### 5.3.5. Out of Order Commits (Commit вне порядка)

С версии 1.3.8 commits можно делать out of order: framework откладывает их, пока не заполнятся «дыры». Приложению не нужно самому вести порядок offsets. Отложенные commits повышают риск дубликатов при падении.

Включение: `maxDeferredCommits` у `ReceiverOptions`. Если отложенных offset commits больше значения, consumer `pause()` до тех пор, пока приложение не acknowledge/commit «пропущенные» offsets.

```java
ReceiverOptions<Object, Object> options = ReceiverOptions.create()
    .maxDeferredCommits(100)
    .subscription(Collections.singletonList("someTopic"));
```

**Разбор операторов и ключей**

- `ReceiverOptions.create()` — опции без map (defaults + дальнейшие сеттеры).
- `maxDeferredCommits(100)` — агрегат отложенных commits по всем assigned topics/partitions; 0 (default) выключает feature.
- `subscription(Collections.singletonList("someTopic"))` — один topic.

#### 5.3.6. Auto-acknowledgement of batches of records

`KafkaReceiver#receiveAutoAck` возвращает `Flux` батчей records каждого `KafkaConsumer#poll()`. Records батча automatically acknowledge, когда Flux батча завершается.

```java
KafkaReceiver.create(receiverOptions)
             .receiveAutoAck()
             .concatMap(r -> r)                                      // (1)
             .subscribe(r -> System.out.println("Received: " + r));  // (2)
```

1. Конкатенация батчей по порядку.
2. Печать record; явный ack не нужен.

**Разбор операторов и ключей**

- `receiveAutoAck()` — Flux батчей poll.
- `concatMap(r -> r)` — разворачивает батчи последовательно (сохраняет порядок).
- `subscribe` — потребление flattened records.
- Размер батча: `KafkaConsumer` property `MAX_POLL_RECORDS` плюс fetch size и wait times. Acknowledge после terminate Flux батча; commit по interval/batch size. Простой режим, но не at-least-once.

#### 5.3.7. Manual acknowledgement of batches of records

`KafkaReceiver#receiveBatch` — Flux батчей `poll()`. Records нужно acknowledge или commit вручную.

```java
KafkaReceiver.create(receiverOptions)
             .receiveBatch()
             .concatMap(b -> b)                                      // (1)
             .subscribe(r -> {
                 System.out.println("Received message: " + r);       // (2)
                 r.receiverOffset().acknowledge();                   // (3)
             });
```

1. Конкатенация по порядку.
2. Печать record.
3. Явный ack каждого сообщения.

**Разбор операторов и ключей**

- `receiveBatch()` — батчи как Flux `ReceiverRecord` с `ReceiverOffset`.
- `concatMap(b -> b)` — последовательный flatten.
- `acknowledge()` — ручной ack (как `receive()`).
- `MAX_POLL_RECORDS` ограничивает размер батча. Сочетает batch-режим `receiveAutoAck` и ручной ack `receive` — удобно для at-least-once.

#### 5.3.8. Disabling automatic commits (Отключение automatic commits)

Если offset commits в Kafka не нужны, не вызывайте acknowledge на records из `receive()`.

```java
receiverOptions = ReceiverOptions.<Integer, String>create()
        .commitInterval(Duration.ZERO)             // (1)
        .commitBatchSize(0);                       // (2)
KafkaReceiver.create(receiverOptions)
             .receive()
             .subscribe(r -> process(r));          // (3)
```

1. Выключить периодические commits.
2. Выключить commits по batch size.
3. Обработка без acknowledge.

**Разбор операторов и ключей**

- `commitInterval(Duration.ZERO)` — нет interval-commits.
- `commitBatchSize(0)` — нет size-commits.
- `receive()` / `subscribe` — consume без ack.

#### 5.3.9. At-most-once delivery

Automatic commits можно отключить, чтобы избежать re-delivery. `ConsumerConfig#AUTO_OFFSET_RESET_CONFIG` = `"latest"` читает только новые records, но при падении и рестарте непредсказуемое число records может быть пропущено.

`KafkaReceiver#receiveAtmostOnce` даёт at-most-once с настраиваемым числом records-per-partition, которые можно потерять при падении. Offsets commit синхронно до dispatch record. Повторной доставки нет, но часть records может не обработаться, если падение после commit и до обработки.

Режим дорогой: каждый record commit отдельно, доставка ждёт успешного commit. `ReceiverOptions#atmostOnceCommitCommitAheadSize` снижает стоимость и избегает блокировки перед dispatch, если offset уже commit. По умолчанию commit-ahead выключен: максимум один потерянный record на partition. При commit-ahead максимум потерь на partition: `atmostOnceCommitCommitAheadSize + 1`.

```java
KafkaReceiver.create(receiverOptions)
             .receiveAtmostOnce()
             .subscribe(r -> System.out.println("Received: " + r));  // (1)
```

1. Обработка record; при ошибке обработки повторной доставки не будет.

**Разбор операторов и ключей**

- `receiveAtmostOnce()` — Flux с commit offset до onNext.
- `subscribe` — обработка без повторной доставки.

#### 5.3.10. Partition assignment and revocation listeners

Можно включить assignment и revocation listeners при назначении/отзыве partitions.

При group management assignment listeners вызываются после rebalance. При manual assignment — при старте consumer. В listener можно seek к нужным offsets. Если до rebalance пользователь pause topics/partitions: при `pauseAllAfterRebalance=false` paused остаются paused; при `true` после rebalance pause все assigned topics/partitions.

Revocation listeners при group management — после rebalance; при manual assignment — перед close consumer. Там можно commit обработанных offsets при ручных commits. При automatic commits acknowledged offsets commit при revocation автоматически.

#### 5.3.11. Controlling start offsets for consuming records

По умолчанию consume с last committed offset каждой assigned partition. Если committed offset нет — стратегия `ConsumerConfig#AUTO_OFFSET_RESET_CONFIG` (`earliest`/`latest`). Можно seek в assignment listener. На `ReceiverPartition`: earliest, latest, конкретный offset или record с timestamp позже заданного времени.

```java
void seekToBeginning();
void seekToEnd();
void seek(long offset);
void seekToTimestamp(long timestamp);
```

**Разбор операторов и ключей**

- `seekToBeginning()` — earliest offset partition.
- `seekToEnd()` — latest offset.
- `seek(long)` — абсолютный offset.
- `seekToTimestamp(long)` — первый record с timestamp позже указанного.

Пример: consume с latest offset:

```java
receiverOptions = receiverOptions
            .addAssignListener(partitions -> partitions.forEach(p -> p.seekToEnd())) // (1)
            .subscription(Collections.singleton(topic));
KafkaReceiver.create(receiverOptions).receive().subscribe();
```

1. Seek к последнему offset каждой assigned partition.

**Разбор операторов и ключей**

- `addAssignListener` — callback на assignment; `partitions` — коллекция `ReceiverPartition`.
- `seekToEnd()` — старт с конца log.
- `subscription` / `KafkaReceiver.create` / `receive` / `subscribe` — обычный consume.

Другие методы `ReceiverPartition` на момент assignment:

```java
long position();
Long beginningOffset();
Long endOffset();
```

**Разбор операторов и ключей**

- `position()` — текущая позиция consumer.
- `beginningOffset()` / `endOffset()` — границы log на момент assignment.

#### 5.3.12. Consumer lifecycle (Жизненный цикл consumer)

Каждый `KafkaReceiver` связан с `KafkaConsumer`, создаваемым при subscribe на inbound Flux одного из receive-методов. Consumer жив, пока Flux не завершится. При complete все acknowledged offsets commit, consumer закрывается.

В один момент активна только одна receive-операция на `KafkaReceiver`. Любой receive можно вызвать снова после terminate предыдущего receive Flux.

### 5.4. Micrometer Metrics

Чтобы включить Micrometer metrics для подлежащих Kafka Consumers и Producers, добавьте `MicrometerConsumerListener` в `ReceiverOptions` или `MicrometerProducerListener` в `SenderOptions`.

### 5.5. Micrometer Observation

Чтобы включить Micrometer observation для produced/consumed records, добавьте `ObservationRegistry` в `SenderOptions` и `ReceiverOptions` через `withObservation()`. Можно задать свой `KafkaSenderObservationConvention` и `KafkaReceiverObservationConvention`. Defaults — в `KafkaSenderObservation` и `KafkaReceiverObservation`. `DefaultKafkaSenderObservationConvention` публикует low-cardinality tags: `reactor.kafka.type = sender` и `reactor.kafka.client.id` из `ProducerConfig.CLIENT_ID_CONFIG` либо identity hash `DefaultKafkaSender` с префиксом `reactor-kafka-sender-`. `DefaultKafkaReceiverObservationConvention`: `reactor.kafka.type = receiver` и `reactor.kafka.client.id` из `ConsumerConfig.CLIENT_ID_CONFIG` либо hash `DefaultKafkaReceiver` с префиксом `reactor-kafka-receiver-`.

Если на `ObservationRegistry` настроен `PropagatingSenderTracingObservationHandler`, tracing из context вокруг producer record пишется в headers до публикации. Если есть `PropagatingReceiverTracingObservationHandler`, tracing из headers восстанавливается в context на receiver со child span.

Из-за обратного порядка Reactor context observation на `KafkaReceiver` ограничена одним `trace` log на каждый received record. Восстановленный tracing попадёт в логи, если так настроена logging-система. Чтобы продолжить observation на consumer, вручную используйте `KafkaReceiverObservation.RECEIVER_OBSERVATION` в операторе обработки:

```java
KafkaReceiver.create(receiverOptions.subscription(List.of(topic)))
        .receive()
        .flatMap(record -> {
            Observation receiverObservation =
                KafkaReceiverObservation.RECEIVER_OBSERVATION.start(null,
                        KafkaReceiverObservation.DefaultKafkaReceiverObservationConvention.INSTANCE,
                        () ->
                                new KafkaRecordReceiverContext(
                                    record, "user.receiver", receiverOptions.bootstrapServers()),
                        observationRegistry);

            return Mono.just(record)
                    .flatMap(TARGET_RECORD_HANDLER)
                    .doOnTerminate(receiverObservation::stop)
                    .doOnError(receiverObservation::error)
                    .contextWrite(context -> context.put(ObservationThreadLocalAccessor.KEY, receiverObservation));
        })
        .subscribe();
```

**Разбор операторов и ключей**

- `subscription(List.of(topic))` — список topics.
- `receive()` — inbound Flux.
- `flatMap` — асинхронная обработка каждого record.
- `KafkaReceiverObservation.RECEIVER_OBSERVATION.start(...)` — старт Observation с convention и `KafkaRecordReceiverContext` (record, логическое имя, `bootstrapServers()`).
- `Mono.just` / `flatMap(TARGET_RECORD_HANDLER)` — прикладная обработка.
- `doOnTerminate` / `doOnError` — `stop` и `error` observation.
- `contextWrite` + `ObservationThreadLocalAccessor.KEY` — кладёт Observation в Reactor context.
- `subscribe()` — запуск.


## 6. Sample Scenarios (Типовые сценарии)

Этот раздел показывает фрагменты кода типичных сценариев Reactor Kafka API. Полные listing: [samples sub-project](https://github.com/reactor/reactor-kafka/tree/main/reactor-kafka-samples).

### 6.1. Sending records to Kafka

См. KafkaSender API. Ниже простой pipeline: send в Kafka и обработка responses. Исходящий поток стартует при subscribe на возвращённый Flux.

```java
KafkaSender.create(SenderOptions.<Integer, String>create(producerProps).maxInFlight(512))   // (1)
           .send(outbound.map(r -> senderRecord(r)))                                        // (2)
           .doOnNext(result -> processResponse(result))                                     // (3)
           .doOnError(e -> processError(e));
```

1. Sender с максимум 512 in-flight сообщений.
2. Send последовательности sender records.
3. Обработка send result в `onNext`.

**Разбор операторов и ключей**

- `SenderOptions.create(producerProps)` — опции из producer props.
- `maxInFlight(512)` — лимит in-flight.
- `KafkaSender.create` / `send` — отправка `Publisher` records.
- `outbound.map(r -> senderRecord(r))` — преобразование исходных элементов в `SenderRecord`.
- `doOnNext` / `doOnError` — обработка успеха и ошибки (цепочка без `subscribe` в фрагменте — subscribe снаружи).

### 6.2. Replaying records from Kafka topics

См. KafkaReceiver API. Flux replay всех records topic и commit offsets после обработки. Ручной acknowledge даёт at-least-once.

```java
ReceiverOptions<Integer, String> options =
    ReceiverOptions.<Integer, String>create(consumerProps)
                   .consumerProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")  // (1)
                   .commitBatchSize(10)                                                    // (2)
                   .subscription(Collections.singleton("demo-topic"));                     // (3)
KafkaReceiver.create(options)
             .receive()
             .doOnNext(r -> {
                     processRecord(r);                   // (4)
                     r.receiverOffset().acknowledge();   // (5)
                 })
             .subscribe();
```

1. Старт с first available offset partition, если committed offsets нет.
2. Commit каждые 10 acknowledged сообщений.
3. Topics для consume.
4. Обработка consumer record.
5. Acknowledge.

**Разбор операторов и ключей**

- `consumerProperty(AUTO_OFFSET_RESET_CONFIG, "earliest")` — `auto.offset.reset=earliest`.
- `commitBatchSize(10)` — commit после 10 ack.
- `subscription` — topic `demo-topic`.
- `receive` / `doOnNext` / `acknowledge` / `subscribe`.

### 6.3. Reactive pipeline with Kafka sink

Consume из внешнего источника, transform, запись в Kafka. У producer много retries, чтобы transient failures не ломали pipeline. Source commits только после успешной записи в Kafka.

```java
senderOptions = senderOptions
    .producerProperty(ProducerConfig.ACKS_CONFIG, "all")                  // (1)
    .producerProperty(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE)   // (2)
    .maxInFlight(128);                                                    // (3)
KafkaSender.create(senderOptions)
           .send(source.flux().map(r -> transform(r)))                      // (4)
           .doOnError(e-> log.error("Send failed, terminating.", e))        // (5)
           .doOnNext(r -> source.commit(r.correlationMetadata()))           // (6)
           .retryWhen(Retry.backoff(3, Duration.of(10L, ChronoUnit.SECONDS)));
```

1. `acks=all`: acknowledge после доставки всем in-sync replicas.
2. Большое число retries producer против transient failures broker.
3. Низкий in-flight, чтобы не заполнять buffer producer и не блокировать pipeline; default `stopOnError=true`.
4. Receive из внешнего source, transform, send в Kafka.
5. Ошибка send — катастрофа, terminate pipeline.
6. Correlation metadata sender record — для commit source record.

**Разбор операторов и ключей**

- `producerProperty(ACKS_CONFIG, "all")` — `acks`.
- `producerProperty(RETRIES_CONFIG, Integer.MAX_VALUE)` — `retries`.
- `maxInFlight(128)` — backpressure.
- `source.flux().map(transform)` — внешний publisher → records.
- `send` / `doOnError` / `doOnNext` / `source.commit(correlationMetadata())`.
- `retryWhen(Retry.backoff(3, ...))` — повтор всего send Flux.

### 6.4. Reactive pipeline with Kafka source

Consume из Kafka topics, transform, вывод во внешний sink. Consumer offsets commit после успешной записи в sink.

```java
receiverOptions = receiverOptions
    .commitInterval(Duration.ZERO)              // (1)
    .commitBatchSize(0)                         // (2)
    .subscription(Pattern.compile(topics));     // (3)
KafkaReceiver.create(receiverOptions)
             .receive()
             .publishOn(aBoundedElasticScheduler) // (4)
             .concatMap(m -> sink.store(transform(m))                                   // (5)
                               .doOnSuccess(r -> m.receiverOffset().commit().block()))  // (6)
             .retryWhen(Retry.backoff(3, Duration.of(10L, ChronoUnit.SECONDS)));
```

1. Выключить периодические commits.
2. Выключить commits по batch size.
3. Wildcard subscription.
4. Нельзя блокировать receiver thread.
5. Transform Kafka record и store во внешний sink.
6. Синхронный commit после успешной доставки в sink.

**Разбор операторов и ключей**

- `commitInterval(Duration.ZERO)` / `commitBatchSize(0)` — только ручной commit.
- `subscription(Pattern.compile(topics))` — pattern topics.
- `publishOn(aBoundedElasticScheduler)` — смена scheduler (для `block()`).
- `concatMap` — последовательная обработка (порядок).
- `sink.store` — внешний I/O как publisher/Mono.
- `commit().block()` — синхронный commit.
- `retryWhen` — пересоздание consumer после ошибок.

### 6.5. Reactive pipeline with Kafka source and sink

Consume из Kafka topic, transform, запись в Kafka topics. Manual acknowledgement — at-least-once: ack после доставки outbound records. Acknowledged offsets commit периодически по commit interval.

```java
receiverOptions = receiverOptions
    .commitInterval(Duration.ofSeconds(10))        // (1)
    .subscription(Pattern.compile(topics));
sender.send(KafkaReceiver.create(receiverOptions)
                         .receive()
                         .map(m -> SenderRecord.create(transform(m.value()), m.receiverOffset())))  // (2)
      .doOnNext(m -> m.correlationMetadata().acknowledge());  // (3)
```

1. Интервал automatic commits.
2. Transform inbound; outbound `SenderRecord` с payload и inbound offset как correlation metadata.
3. Acknowledge inbound offset из correlation metadata после доставки outbound в Kafka.

**Разбор операторов и ключей**

- `commitInterval(Duration.ofSeconds(10))` — periodic commit.
- `receive` / `map` / `SenderRecord.create(transformed, receiverOffset)`.
- `sender.send` — исходящий Flux.
- `correlationMetadata().acknowledge()` — metadata типизирована как `ReceiverOffset`.

### 6.6. At-most-once delivery

Producer не ждёт acks и не делает retries. Сообщения, не доставленные с первой попытки, отбрасываются. `KafkaReceiver` commit offsets до доставки в приложение: при рестарте нет redelivery. При replication factor 1 этот код — at-most-once.

```java
senderOptions = senderOptions
    .producerProperty(ProducerConfig.ACKS_CONFIG, "0")     // (1)
    .producerProperty(ProducerConfig.RETRIES_CONFIG, "0")  // (2)
    .stopOnError(false);                                   // (3)
receiverOptions = receiverOptions
    .subscription(Collections.singleton(sourceTopic));
KafkaSender.create(senderOptions)
            .send(KafkaReceiver.create(receiverOptions)
                               .receiveAtmostOnce()                   // (4)
                               .map(cr -> SenderRecord.create(transform(cr.value()), cr.offset())));
```

1. `acks=0`: send complete после локальной буферизации, до доставки в broker.
2. Нет retries producer.
3. Игнорировать error и слать остальные records.
4. At-most-once receive.

**Разбор операторов и ключей**

- `ACKS_CONFIG` = `"0"`, `RETRIES_CONFIG` = `"0"`.
- `stopOnError(false)` — не рвать Flux на первом failed send.
- `receiveAtmostOnce()` — commit до dispatch.
- `SenderRecord.create(..., cr.offset())` — offset как correlation metadata.

### 6.7. Fan-out with Multiple Streams

Fan-out: те же records в нескольких независимых streams. Каждый stream на своём потоке: transform и запись в Kafka topic.

[EmitterProcessor](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/EmitterProcessor.html) рассылает inbound records нескольким subscribers.

```java
EmitterProcessor<Person> processor = EmitterProcessor.create();         // (1)
BlockingSink<Person> incoming = processor.connectSink();                // (2)
inputRecords = KafkaReceiver.create(receiverOptions)
                            .receive()
                            .doOnNext(m -> incoming.emit(m.value()));   // (3)

outputRecords1 = processor.publishOn(scheduler1).map(p -> process1(p)); // (4)
outputRecords2 = processor.publishOn(scheduler2).map(p -> process2(p)); // (5)

Flux.merge(sender.send(outputRecords1), sender.send(outputRecords2))
    .doOnSubscribe(s -> inputRecords.subscribe())
    .subscribe();                                                       // (6)
```

1. Publish/subscribe `EmitterProcessor` для fan-out inbound records.
2. `BlockingSink`, в который emit records.
3. Receive из Kafka и emit в sink.
4. Consume на scheduler, process, outbound records.
5. Второй processor тех же данных на другом scheduler.
6. Merge streams и subscribe — старт потока.

**Разбор операторов и ключей**

- `EmitterProcessor.create()` — multicast processor (в новых Reactor заменён Sinks/processors).
- `connectSink()` / `emit` — ручная эмиссия.
- `receive` / `doOnNext`.
- `publishOn(scheduler1/2)` — разные threads.
- `map(process1/process2)` — независимые transforms.
- `Flux.merge(sender.send(...), sender.send(...))` — слияние send results.
- `doOnSubscribe` — старт inbound при подписке на merge.
- `subscribe()` — запуск.

### 6.8. Concurrent Processing with Partition-Based Ordering

Consume из topic, обработка несколькими потоками, запись в другой topic. Group by partition сохраняет порядок обработки и commit. Каждая partition — один поток.

```java
Scheduler scheduler = Schedulers.newElastic("sample", 60, true);
KafkaReceiver.create(receiverOptions)
             .receive()
             .groupBy(m -> m.receiverOffset().topicPartition())                  // (1)
             .flatMap(partitionFlux ->
                 partitionFlux.publishOn(scheduler)
                              .map(r -> processRecord(partitionFlux.key(), r))
                              .sample(Duration.ofMillis(5000))                   // (2)
                              .concatMap(offset -> offset.commit()));            // (3)
```

1. Group by partition — порядок.
2. Периодический commit.
3. Commit по порядку через `concatMap`.

**Разбор операторов и ключей**

- `Schedulers.newElastic("sample", 60, true)` — elastic scheduler (устаревшее имя; аналог boundedElastic), TTL 60s, daemon.
- `groupBy(topicPartition())` — `GroupedFlux` на partition.
- `flatMap` — параллельная обработка групп.
- `publishOn(scheduler)` — свой поток на группу.
- `map(processRecord)` — обработка; предполагается возврат offset.
- `sample(Duration.ofMillis(5000))` — периодическая выборка для commit.
- `concatMap(offset -> offset.commit())` — последовательные commits.

### 6.9. Transactional send

Consume из внешнего source, transform, несколько transformed records в разные Kafka topics в одной transaction.

```java
senderOptions = senderOptions
    .producerProperty(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "SampleTxn");       // (1)
KafkaSender.create(senderOptions)
           .sendTransactionally(source.map(r -> Flux.fromIterable(transform(r)))) // (2)
           .concatMap(r -> r)
           .doOnError(e-> log.error("Send failed, terminating.", e))
           .doOnNext(r -> log.debug("Send completed {}", r.correlationMetadata());
```

1. `transactional.id` producer.
2. Несколько records от каждого source record в одной transaction.

**Разбор операторов и ключей**

- `TRANSACTIONAL_ID_CONFIG` (`transactional.id`) — обязателен для Kafka transactions.
- `sendTransactionally` — каждый внутренний Flux — одна transaction.
- `source.map(r -> Flux.fromIterable(transform(r)))` — publisher транзакций.
- `concatMap(r -> r)` — flatten результатов по порядку.
- `doOnError` / `doOnNext` / `correlationMetadata()`.

(В исходном фрагменте у последнего `log.debug` нет закрывающей `)` — скопировано как в reference.)

### 6.10. Exactly-once delivery

Exactly-once: source records из Kafka topic transform и send в Kafka. Каждый batch приходит в новой transaction. Offsets source records батча automatically commit внутри той же transaction. Приложение commit transaction после успешной доставки transformed records в destination topic. Следующий batch — в новой transaction после commit текущей.

```java
senderOptions = senderOptions
    .producerProperty(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "SampleTxn");    // (1)
receiverOptions = receiverOptions
    .consumerProperty(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed") // (2)
    .subscription(Collections.singleton(sourceTopic));
sender = KafkaSender.create(senderOptions);
transactionManager = sender.transactionManager();
receiver.receiveExactlyOnce(transactionManager)                                // (3)
        .concatMap(f -> sender.send(f.map(r -> transform(r)))                  // (4)
                              .concatWith(transactionManager.commit()))        // (5)
        .onErrorResume(e -> transactionManager.abort().then(Mono.error(e)))    // (6)
```

1. `transactional.id` producer.
2. Consume только committed messages.
3. Exactly-once receive внутри transactions; offsets auto-commit при commit transaction.
4. Send transformed records в той же transaction, что и source offsets.
5. Commit transaction после успешных send.
6. Abort transaction при ошибке send и проброс error.

**Разбор операторов и ключей**

- `TRANSACTIONAL_ID_CONFIG` — transactional producer.
- `ISOLATION_LEVEL_CONFIG` = `read_committed` — не читать aborted transactions.
- `sender.transactionManager()` — менеджер begin/commit/abort.
- `receiveExactlyOnce(transactionManager)` — Flux батчей, привязанных к transaction.
- `concatMap` — один батч/transaction за раз.
- `sender.send` внутри той же transaction.
- `concatWith(transactionManager.commit())` — commit после send.
- `onErrorResume` + `transactionManager.abort()` + `Mono.error(e)` — abort и error downstream.

Last updated 2025-11-06 15:37:03 UTC (18:37 MSK, 2025-11-06).
