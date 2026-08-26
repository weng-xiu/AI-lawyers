@echo off
REM 12348 公共法律服务热线 —— 全容器化一键部署（双击运行）
chcp 65001 >nul
cd /d "%~dp0"
echo.
echo  ============================================================
echo   12348 公共法律服务热线 一键容器化部署
echo  ============================================================
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy.ps1" %*
echo.
pause
