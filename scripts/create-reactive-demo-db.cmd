@echo off
REM ============================================================================
REM create-reactive-demo-db.cmd
REM
REM НАЗНАЧЕНИЕ: создать базу reactive_demo в ЛОКАЛЬНОМ Docker Postgres (local-postgres).
REM ЗАЧЕМ:     модуль reactive-demo подключается к БД reactive_demo на localhost:5432.
REM ГДЕ:       только ваш ПК — удалённый сервер не затрагивается.
REM
REM Запуск (из корня репозитория):
REM   scripts\create-reactive-demo-db.cmd
REM
REM Git Bash:
REM   bash scripts/create-reactive-demo-db.sh
REM
REM Документация: scripts\local-reactive-demo-db.md
REM ============================================================================
chcp 65001 >nul 2>&1
setlocal EnableDelayedExpansion

set "COMPOSE_DIR=%~dp0..\app\src\main\resources\docker-greeting"

call :wait_postgres
if errorlevel 1 (
  echo [INFO] local-postgres ne zapuschen, probuyu docker compose up...
  if not exist "%COMPOSE_DIR%\docker-compose.yml" (
    echo [ERROR] Ne naiden docker-compose: %COMPOSE_DIR%
    echo         cd app\src\main\resources\docker-greeting ^&^& docker compose up -d
    exit /b 1
  )
  pushd "%COMPOSE_DIR%"
  docker compose up -d postgres reactive-demo-db-init
  set "COMPOSE_ERR=!errorlevel!"
  popd
  if !COMPOSE_ERR! neq 0 (
    echo [ERROR] docker compose up ne udalosya.
    exit /b 1
  )
  call :wait_postgres 30
  if errorlevel 1 (
    echo [ERROR] Kontejner local-postgres ne zapuschen. Proverite Docker Desktop.
    exit /b 1
  )
)

docker exec local-postgres psql -U app -d app -tc "SELECT 1 FROM pg_database WHERE datname='reactive_demo'" 2>nul | findstr /r "1" >nul
if errorlevel 1 (
  echo Sozdayu bazu reactive_demo...
  docker exec local-postgres psql -U app -d app -v ON_ERROR_STOP=1 -c "CREATE DATABASE reactive_demo OWNER app;"
  if errorlevel 1 exit /b 1
  echo Gotovo: reactive_demo sozdana.
) else (
  echo Baza reactive_demo uzhe sushchestvuet.
)

endlocal
exit /b 0

:wait_postgres
set "MAX_WAIT=%~1"
if "%MAX_WAIT%"=="" set "MAX_WAIT=1"
set /a "TRIES=0"
:wait_loop
docker ps --filter "name=local-postgres" --filter "status=running" --format "{{.Names}}" 2>nul | findstr /i "local-postgres" >nul
if not errorlevel 1 exit /b 0
set /a "TRIES+=1"
if !TRIES! geq %MAX_WAIT% exit /b 1
ping -n 2 127.0.0.1 >nul
goto wait_loop
