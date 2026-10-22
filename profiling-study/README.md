# profiling-study

Курс к собеседованию по профилированию Java-сервиса: JDK 27, алгоритм поиска причины замедления до профайлера, консольные утилиты JDK, JFR и JMC, CPU и аллокации. Теория с цитатами из первоисточников, практика с тестами и лабораторные на учебном сервисе.

Начать с плана: [docs/profiling-course/00-plan.md](docs/profiling-course/00-plan.md).

## Запуск тестов

```bash

cd /mnt/d/Project_infra/greeting-service-infra/profiling-study
./gradlew test
```

## Лаборатория

```bash

cd /mnt/d/Project_infra/greeting-service-infra/profiling-study
./gradlew -q classes
java -cp build/classes/java/main com.example.profcourse.lab.LabApp
```
