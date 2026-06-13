@echo off
chcp 65001 >nul

set "TARGET=D:\Project_infra\greeting-service-infra"
set "LINK=D:\!_Проекты инфраструктуры\greeting-service-infra"

echo Cursor: привязка истории чата к полному репозиторию
echo.
echo TARGET: %TARGET%
echo LINK:   %LINK%
echo.

if not exist "%TARGET%\app\build.gradle" (
  echo ERROR: Нет репозитория в TARGET
  pause
  exit /b 1
)

if exist "%LINK%" (
  dir "%LINK%" 2>nul | findstr /i "<JUNCTION>" >nul
  if not errorlevel 1 (
    echo Junction уже создан.
    goto done
  )
  echo.
  echo В %LINK% есть обычная папка ^(не junction^).
  echo Закройте Cursor, переименуйте её вручную, например:
  echo   greeting-service-infra.old
  echo и запустите этот скрипт снова.
  pause
  exit /b 1
)

if not exist "D:\!_Проекты инфраструктуры" mkdir "D:\!_Проекты инфраструктуры"

mklink /J "%LINK%" "%TARGET%"
if errorlevel 1 (
  echo ERROR: mklink не удался
  pause
  exit /b 1
)

:done
echo.
echo Готово.
echo.
echo 1. Cursor: File -^> Open Folder
echo    %LINK%
echo 2. Этот чат и дерево файлов будут совпадать.
echo    Физически файлы в Project_infra.
echo.
pause
