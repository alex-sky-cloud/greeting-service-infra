# ─────────────────────────────────────────────────────────────────────────────
# Stage 1: Build — сборка JAR внутри контейнера.
# Используем официальный образ Gradle + Eclipse Temurin JDK 21.
# ─────────────────────────────────────────────────────────────────────────────
FROM gradle:8.8-jdk21 AS build

WORKDIR /workspace

# Копируем файлы сборки отдельно — Docker кэширует слой с зависимостями,
# пока build.gradle и settings.gradle не изменились.
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon -q

# Копируем исходный код и выполняем сборку.
# -x test — тесты запускаются отдельным шагом в pipeline.
COPY src ./src
RUN gradle bootJar -x test --no-daemon

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2: Runtime — минимальный образ только с JRE.
# eclipse-temurin:21-jre-alpine весит ~200 МБ против ~600 МБ у full JDK.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Создаём непривилегированного пользователя. Запускать приложение
# от root в контейнере — плохая практика безопасности.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Копируем fat JAR из build-стадии.
COPY --from=build /workspace/build/libs/app.jar app.jar

# Передаём управление непривилегированному пользователю.
USER appuser

# Порт, который слушает Spring Boot (см. application.yml).
EXPOSE 8080

# JVM флаги для контейнерной среды:
# -XX:+UseContainerSupport — JVM читает CPU/memory limits из cgroups, а не с хоста.
# -XX:MaxRAMPercentage=75 — используем не более 75% лимита памяти контейнера под heap.
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
