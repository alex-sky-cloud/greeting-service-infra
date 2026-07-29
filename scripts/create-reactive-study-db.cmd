@echo off
REM ============================================================================
REM create-reactive-study-db.cmd
REM
REM НАЗНАЧЕНИЕ: поднять локальный PostgreSQL для reactive-study (docker-compose модуля).
REM Запуск из КОРНЯ репозитория:
REM   scripts\create-reactive-study-db.cmd
REM ============================================================================
setlocal

set "SCRIPT_DIR=%~dp0"
set "REPO_ROOT=%SCRIPT_DIR%.."
set "COMPOSE_DIR=%REPO_ROOT%\reactive-study\src\main\resources\docker-reactive-study"

if not exist "%COMPOSE_DIR%\docker-compose.yml" (
  echo [ERROR] Не найден %COMPOSE_DIR%\docker-compose.yml
  exit /b 1
)

if not exist "%COMPOSE_DIR%\.env" (
  echo [INFO] Копирую .env.example -^> .env
  copy /Y "%COMPOSE_DIR%\.env.example" "%COMPOSE_DIR%\.env" >nul
)

echo [INFO] docker compose up -d (docker-reactive-study)
pushd "%COMPOSE_DIR%"
docker compose up -d
popd

timeout /t 5 /nobreak >nul
docker exec reactive-study-postgres pg_isready -U app -d reactive_study >nul 2>&1
if errorlevel 1 (
  echo [ERROR] reactive-study-postgres не готов. Проверьте: docker logs reactive-study-postgres
  exit /b 1
)

echo Готово: reactive_study на localhost:5434 (контейнер reactive-study-postgres).
