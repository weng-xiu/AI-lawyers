@echo off
chcp 65001 >nul
echo.
echo [信息] 使用 Jar 命令运行 Web 工程。
echo.

cd /d %~dp0
cd ..\ai-admin\target

rem C10：JVM 参数必须放在 -jar 之前，否则会被当成应用入参；JDK17 下显式启用 G1GC
set JAVA_OPTS=-Xms256m -Xmx1024m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=512m -XX:+UseG1GC

java %JAVA_OPTS% -jar ai-admin.jar

pause
