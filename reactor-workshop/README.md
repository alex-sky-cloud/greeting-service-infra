# reactor-workshop

Учебный модуль практических заданий по Project Reactor (WebFlux + R2DBC).
План и статусы тем: [PLAN.md](PLAN.md).

## Локальный запуск

```bash
cd src/main/resources/docker-reactor-workshop
cp .env.example .env
docker compose up -d

cd ../../../..
./gradlew bootRun --args='--spring.profiles.active=local'
```

Health: http://localhost:8084/actuator/health

Тема 1: http://localhost:8084/api/t01/map
