@echo off
REM perf\run-baseline.cmd —— 用「固定参数」重启应用，保证每次压测环境一致
REM
REM 【为什么需要这个脚本】
REM   压测的可比性依赖三个东西不变：
REM     1. JVM 堆大小（-Xms/-Xmx）
REM     2. Redis 密码（application.yml 里是 ${REDIS_PASSWORD:}，不设就是空）
REM     3. SQL 日志级别（application.yml 里已经是 info，不要改回 debug）
REM
REM   如果每次手工敲 mvn spring-boot:run，很容易忘设密码 → 缓存静默降级，
REM   压测结果会变成「无缓存版本」，但你不会发现。
REM
REM 【怎么用】
REM   第一次（或密码变了）：
REM     perf\run-baseline.cmd set-password 你的Redis密码
REM
REM   每次压测前启动应用：
REM     perf\run-baseline.cmd
REM
REM   看到 "Started BlogBackendApplication" 后再另开一个终端跑 k6。
REM   Ctrl+C 停止。

setlocal

if /I "%~1"=="set-password" (
    if "%~2"=="" (
        echo [ERROR] 用法: perf\run-baseline.cmd set-password 你的Redis密码
        exit /b 1
    )
    setx REDIS_PASSWORD "%~2" >nul
    echo [OK] 已写入用户级环境变量 REDIS_PASSWORD（下次启动生效）
    echo      注意: 已经打开的终端 / IDEA 看不到新值，需要重开
    exit /b 0
)

if "%REDIS_PASSWORD%"=="" (
    echo [WARN] 当前会话没有 REDIS_PASSWORD，缓存会静默降级为「无缓存」
    echo        先执行: perf\run-baseline.cmd set-password 你的Redis密码
    echo        然后重开终端
    echo.
)

REM 【为什么不在这里加 -Xmx】
REM   基线 A / 基线 B 都是用默认 JVM 堆跑的。现在如果改成 -Xmx512m，
REM   M2 的数字就和前面不可比了。压测的第一原则是「只改一个变量」，
REM   所以这里刻意保持默认堆，和基线 A/B 一致。

cd /d "%~dp0.."
echo [INFO] 工作目录: %CD%
echo [INFO] 启动应用（默认 JVM 参数，与基线 A/B 保持一致）...

call mvn -o spring-boot:run

endlocal
