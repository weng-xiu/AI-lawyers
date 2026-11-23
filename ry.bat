@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

rem jar 包所在目录（脚本位于仓库根目录，产物在 ai-admin/target）
set AppName=ai-admin.jar
set AppDir=%~dp0ai-admin\target

rem JVM 参数（C9：JDK17 已移除 -XX:+PrintGCDetails/-XX:+PrintGCDateStamps，
rem 统一改用 Xlog；G1GC 为 JDK17 默认收集器，显式声明保留意图）
rem 注意：set 不加外层引号，整条在 start 行以 %JVM_OPTS% 展开，避免被当成单个参数
set JVM_OPTS=-Dname=%AppName% -Duser.timezone=Asia/Shanghai -Xms512m -Xmx1024m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:+UseG1GC -Xlog:gc*:file=%AppDir%\logs\gc.log:time,uptime:filecount=10,filesize=10M

:menu
ECHO.
ECHO.  [1] 启动 %AppName%
ECHO.  [2] 关闭 %AppName%
ECHO.  [3] 重启 %AppName%
ECHO.  [4] 运行状态 %AppName%
ECHO.  [5] 退 出
ECHO.
ECHO. 请输入选择的项目编号:
set /p ID=
IF "%id%"=="1" GOTO start
IF "%id%"=="2" GOTO stop
IF "%id%"=="3" GOTO restart
IF "%id%"=="4" GOTO status
IF "%id%"=="5" EXIT
PAUSE
GOTO menu

:start
set pid=
for /f "usebackq tokens=1-2" %%a in (`jps -l ^| findstr %AppName%`) do (
    set pid=%%a
    set image_name=%%b
)
if defined pid (
    echo %AppName% 已在运行，PID=!pid!
    PAUSE
    GOTO menu
)

if not exist "%AppDir%\%AppName%" (
    echo 未找到 %AppDir%\%AppName%，请先执行 bin\package.bat 打包
    PAUSE
    GOTO menu
)
if not exist "%AppDir%\logs" mkdir "%AppDir%\logs"

start "%AppName%" /D "%AppDir%" javaw %JVM_OPTS% -jar %AppName%

echo  starting……
echo  Start %AppName% success...
GOTO menu

rem 通过 jps 命令查询 pid 后，强制结束进程
:stop
set pid=
for /f "usebackq tokens=1-2" %%a in (`jps -l ^| findstr %AppName%`) do (
    set pid=%%a
    set image_name=%%b
)
if not defined pid (
    echo process %AppName% does not exist
) else (
    echo prepare to kill !image_name!
    echo start kill !pid! ...
    taskkill /f /pid !pid!
)
GOTO menu

:restart
call :stop
call :start
GOTO menu

:status
set pid=
for /f "usebackq tokens=1-2" %%a in (`jps -l ^| findstr %AppName%`) do (
    set pid=%%a
    set image_name=%%b
)
if not defined pid (echo process %AppName% is dead ) else (
    echo !image_name! is running, PID=!pid!
)
GOTO menu
