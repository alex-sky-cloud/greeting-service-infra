@echo off
REM ============================================================================
REM get-kubeconfig.cmd
REM
REM НАЗНАЧЕНИЕ: обёртка для Windows — вызывает get-kubeconfig.sh через WSL Ubuntu.
REM ЗАЧЕМ:     двойной клик или cmd без Git Bash; kubeconfig сохраняется в ~/.kube/.
REM БЕЗОПАСНО: только локальный файл, кластер и БД не меняет.
REM
REM Запуск: scripts\get-kubeconfig.cmd
REM ============================================================================
setlocal
set "SCRIPT=%~dp0get-kubeconfig.sh"
for /f "usebackq delims=" %%i in (`wsl -d Ubuntu wslpath -u "%SCRIPT%"`) do set "WSL_SCRIPT=%%i"
wsl -d Ubuntu bash -lc "bash '%WSL_SCRIPT%'"
endlocal
