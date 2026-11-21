# =====================================================================
# 12348 公共法律服务热线 —— 全容器化一键部署（Windows / Docker Desktop）
#
# 完成动作：
#   1. 检查 Docker 环境
#   2. 准备初始化 SQL：默认直接使用仓库基线 deploy/docker/mysql/init/01-init.sql
#      （仅在显式 -DumpFromLocal 时，才从本机 MySQL 导出 ai-law 库覆盖基线）
#   3. 复制 FreeSWITCH 线路主机名修正 SQL 到 init 目录
#   4. docker compose build 构建后端/前端镜像
#   5. docker compose up -d 启动 mysql/redis/freeswitch/backend/frontend
#   6. 等待后端就绪并验证各服务端口
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File deploy\deploy.ps1
# 可选参数：
#   -DumpFromLocal  从本机已有 MySQL 库 ai-law 导出，覆盖仓库基线 01-init.sql
#                   （迁移/同步线上库时使用；空环境请勿使用）
#   -SkipDump       兼容旧参数：现已默认不导出，保留仅为不破坏既有调用脚本
#   -NoBuild        跳过镜像构建
# =====================================================================
param(
    [switch]$DumpFromLocal,
    [switch]$SkipDump,
    [switch]$NoBuild
)

$ErrorActionPreference = "Continue"
# 让原生命令（docker/mysqldump）写到 stderr 的内容不被当成终止错误
$global:NativeCommandUseErrorActionPreference = $false
$DeployDir   = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $DeployDir
$InitSqlDir  = Join-Path $DeployDir "docker\mysql\init"

$DbName = "ai-law"
$DbUser = "root"
$DbPass = "root"

function Step($m){ Write-Host "`n========== $m ==========" -ForegroundColor Cyan }
function Ok($m){ Write-Host "[OK] $m" -ForegroundColor Green }
function Warn($m){ Write-Host "[!] $m" -ForegroundColor Yellow }
function Die($m){ Write-Host "[X] $m" -ForegroundColor Red; exit 1 }
# 执行原生命令并合并 stderr，仅按退出码判断成败
function Invoke-Native { param([scriptblock]$Block)
    $out = & $Block 2>&1
    return [pscustomobject]@{ Code = $LASTEXITCODE; Out = ($out | Out-String) }
}

# ---- 1. 检查 Docker ----
Step "1/6 检查 Docker 环境"
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Die "未找到 docker，请先安装并启动 Docker Desktop。" }
$r = Invoke-Native { docker info }
if ($r.Code -ne 0) { Die "Docker 未运行，请先启动 Docker Desktop 后重试。`n$($r.Out)" }
$r = Invoke-Native { docker compose version }
if ($r.Code -ne 0) { Die "需要 Docker Compose v2（docker compose）。" }
Ok "Docker 与 Compose 可用"

# ---- 2. 准备初始化 SQL ----
Step "2/6 准备数据库初始化脚本"
New-Item -ItemType Directory -Force -Path $InitSqlDir | Out-Null
$dumpFile = Join-Path $InitSqlDir "01-init.sql"

# 线路主机名修正 SQL 复制到 init 目录（重命名为 99 开头，确保在 01 之后执行）
$trunkFixSrc = Join-Path $DeployDir "docker\mysql\99-container-trunk-fix.sql"
$trunkFixDst = Join-Path $InitSqlDir "99-container-trunk-fix.sql"
Copy-Item $trunkFixSrc $trunkFixDst -Force
Ok "已放入线路主机名修正 SQL"

if ($DumpFromLocal) {
    if ($SkipDump) { Warn "-SkipDump 与 -DumpFromLocal 同时指定，按 -DumpFromLocal 执行" }
    $mysqldump = Get-Command mysqldump -ErrorAction SilentlyContinue
    if (-not $mysqldump) { Die "未找到 mysqldump 命令，无法执行 -DumpFromLocal。" }
    Write-Host "正在从本机 $DbName 导出（会覆盖仓库基线 $dumpFile）..."
    $tmpDump = "$dumpFile.tmp"
    if (Test-Path $tmpDump) { Remove-Item $tmpDump -Force }
    # 用 --result-file 直接落盘，避免 PowerShell 管道改写编码/换行（MySQL 容器需无 BOM UTF-8）
    $env:MYSQL_PWD = $DbPass
    & mysqldump -u$DbUser --default-character-set=utf8mb4 `
        --single-transaction --routines --triggers --events `
        --column-statistics=0 `
        --result-file="$tmpDump" $DbName 2>&1 | Out-Null
    $dumpCode = $LASTEXITCODE
    if ($dumpCode -ne 0 -or -not (Test-Path $tmpDump)) {
        # MySQL 5.7 客户端不支持 --column-statistics，去掉重试
        & mysqldump -u$DbUser --default-character-set=utf8mb4 `
            --single-transaction --routines --triggers --events `
            --result-file="$tmpDump" $DbName 2>&1 | Out-Null
        $dumpCode = $LASTEXITCODE
    }
    $env:MYSQL_PWD = ""
    if ($dumpCode -ne 0 -or -not (Test-Path $tmpDump)) {
        if (Test-Path $tmpDump) { Remove-Item $tmpDump -Force }
        Die "从本机导出 $DbName 失败（mysqldump 退出码 $dumpCode），仓库基线未被修改。"
    }
    if ((Get-Item $tmpDump).Length -lt 1024) {
        Remove-Item $tmpDump -Force
        Die "导出的文件过小，疑似空库，仓库基线未被修改。"
    }
    Move-Item $tmpDump $dumpFile -Force
    $size = [math]::Round((Get-Item $dumpFile).Length/1KB,1)
    Ok "已从本机导出并覆盖基线：$dumpFile（${size} KB）"
} else {
    if (-not (Test-Path $dumpFile)) {
        Die "缺少仓库基线 $dumpFile。请从仓库恢复该文件，或使用 -DumpFromLocal 从本机导出。"
    }
    if ($SkipDump) { Warn "已默认使用仓库基线，-SkipDump 参数不再需要" }
    $size = [math]::Round((Get-Item $dumpFile).Length/1KB,1)
    $tableCount = (Select-String -Path $dumpFile -Pattern '^CREATE TABLE' -AllMatches).Count
    Ok "使用仓库基线：$dumpFile（${size} KB，$tableCount 张表）"
}

# ---- 3. 构建镜像 ----
if (-not $NoBuild) {
    Step "3/6 构建后端/前端镜像（首次较慢，Maven + npm，可能需数分钟）"
    Push-Location $DeployDir
    $r = Invoke-Native { docker compose build }
    Write-Host $r.Out
    Pop-Location
    if ($r.Code -ne 0) { Die "镜像构建失败，请查看上方日志。" }
    Ok "镜像构建完成"
} else {
    Step "3/6 跳过镜像构建（-NoBuild）"
}

# ---- 4. 启动服务 ----
Step "4/6 启动全部容器"
Push-Location $DeployDir
$r = Invoke-Native { docker compose up -d }
Write-Host $r.Out
Pop-Location
if ($r.Code -ne 0) { Die "容器启动失败，请查看上方日志。" }
Ok "已下发启动命令"

# ---- 5. 等待服务就绪 ----
Step "5/6 等待服务就绪"
function Test-Tcp($hostName,$port,$timeoutSec){
    $sw=[Diagnostics.Stopwatch]::StartNew()
    while($sw.Elapsed.TotalSeconds -lt $timeoutSec){
        try{
            $c=New-Object Net.Sockets.TcpClient
            $iar=$c.BeginConnect($hostName,$port,$null,$null)
            if($iar.AsyncWaitHandle.WaitOne(800,$false) -and $c.Connected){ $c.Close(); return $true }
            $c.Close()
        }catch{}
        Start-Sleep -Milliseconds 1500
    }
    return $false
}

Write-Host "等待 MySQL(3306)..."
$null = Test-Tcp "localhost" 3306 90
Write-Host "等待 Redis(6379)..."
$null = Test-Tcp "localhost" 6379 60
Write-Host "等待后端容器启动（P3-⑫：8080 仅容器内网，Spring Boot 约需 30-60 秒）..."
$backendReady = $false
for ($i = 0; $i -lt 50; $i++) {
    if ((docker inspect -f '{{.State.Running}}' ai12348-backend 2>$null) -eq 'true') { $backendReady = $true; break }
    Start-Sleep -Seconds 3
}
if ($backendReady) { Ok "后端容器已运行（端口仅容器内网）" } else { Warn "后端容器未运行，请用 docker logs ai12348-backend 查看" }
Write-Host "等待 前端(80)..."
if (Test-Tcp "localhost" 80 90) { Ok "前端端口 80 已就绪" } else { Warn "前端 80 等待超时" }

# ---- 6. 状态汇总 ----
Step "6/6 容器状态"
Push-Location $DeployDir
docker compose ps
Pop-Location

Write-Host "`n======================================================================" -ForegroundColor Cyan
Ok "部署流程完成！"
Write-Host ""
Write-Host "访问入口：" -ForegroundColor White
Write-Host "  管理后台/工作台 : http://localhost" -ForegroundColor Green
Write-Host "  HTTPS（自签）   : https://localhost  （生产挂载正式证书并设 TLS_FORCE_REDIRECT=true）" -ForegroundColor Green
Write-Host "  后端接口       : 仅容器内网 :8080（统一经前端 /prod-api 访问）" -ForegroundColor Gray
Write-Host "  Druid 监控     : 默认关闭；如需开启请配置强口令并从容器内网访问 /druid" -ForegroundColor Gray
Write-Host ""
Write-Host "常用命令（在 deploy 目录执行）：" -ForegroundColor White
Write-Host "  docker compose logs -f backend     # 查看后端日志" -ForegroundColor Gray
Write-Host "  docker compose logs -f freeswitch  # 查看 FreeSWITCH 日志" -ForegroundColor Gray
Write-Host "  docker compose restart backend     # 重启后端" -ForegroundColor Gray
Write-Host "  docker compose down                # 停止全部" -ForegroundColor Gray
Write-Host ""
Write-Host "FreeSWITCH 律师分机 1003-1006 已通过卷挂载；首次启动如未生效，执行：" -ForegroundColor White
Write-Host "  docker exec ai12348-freeswitch /usr/local/freeswitch/bin/fs_cli -x 'reloadxml'" -ForegroundColor Gray
Write-Host "======================================================================" -ForegroundColor Cyan
