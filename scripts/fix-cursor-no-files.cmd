@echo off
chcp 65001 >nul
set "SRC=D:\Project_infra\greeting-service-infra"
set "DST=D:\!_Проекты инфраструктуры\greeting-service-infra"

echo === Cursor: показать файлы в этом чате ===
echo SRC: %SRC%
echo DST: %DST%
echo.

if not exist "%SRC%\app\build.gradle" (
  echo ERROR: нет репозитория в %SRC%
  pause & exit /b 1
)

if not exist "D:\!_Проекты инфраструктуры" mkdir "D:\!_Проекты инфраструктуры"

if exist "%DST%" (
  dir "%DST%" | findstr /i "<JUNCTION>" >nul && goto ok
  echo Папка DST существует. Переименуйте её в greeting-service-infra.old и запустите снова.
  pause & exit /b 1
)

mklink /J "%DST%" "%SRC%"
if errorlevel 1 (
  echo mklink failed. Запустите cmd от имени администратора.
  pause & exit /b 1
)

:ok
echo.
echo OK. В Cursor: File -^> Open Folder -^> 
echo   %DST%
echo Затем Ctrl+Shift+P -^> Developer: Reload Window
pause
