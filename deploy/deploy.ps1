# =====================================================================
# 12348 公共法律服务热线 —— 全容器化一键部署（Windows / Docker Desktop）
#
# 完成动作：
#   1. 检查 Docker 环境
#   2. 从本地 MySQL 导出业务库结构+基础数据 -> deploy/docker/mysql/init/01-init.sql
#   3. 复制 FreeSWITCH 线路主机名修正 SQL 到 init 目录
#   4. docker compose build 构建后端/前端镜像
#   5. docker compose up -d 启动 mysql/redis/freeswitch/backend/frontend
#   6. 等待后端就绪并验证各服务端口
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File deploy\deploy.ps1
# 可选参数：
#   -SkipDump   跳过数据库导出（使用已存在的 01-init.sql）
#   -NoBuild    跳过镜像构建
# =====================================================================
param(
    [switch]$SkipDump,
    [switch]$NoBuild
)

$ErrorActionPreference = "Stop"
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

# ---- 1. 检查 Docker ----
Step "1/6 检查 Docker 环境"
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Die "未找到 docker，请先安装并启动 Docker Desktop。" }
docker info *> $null
if ($LASTEXITCODE -ne 0) { Die "Docker 未运行，请先启动 Docker Desktop 后重试。" }
$compose = if (docker compose version *> $null) { "docker compose" } else { Die "需要 Docker Compose v2（docker compose）。" }
Ok "Docker 与 Compose 可用"

# ---- 2. 导出数据库 ----
Step "2/6 导出业务数据库 $DbName"
New-Item -ItemType Directory -Force -Path $InitSqlDir | Out-Null
$dumpFile = Join-Path $InitSqlDir "01-init.sql"

# 线路主机名修正 SQL 复制到 init 目录（重命名为 99 开头，确保在 01 之后执行）
$trunkFixSrc = Join-Path $DeployDir "docker\mysql\99-container-trunk-fix.sql"
$trunkFixDst = Join-Path $InitSqlDir "99-container-trunk-fix.sql"
Copy-Item $trunkFixSrc $trunkFixDst -Force
Ok "已放入线路主机名修正 SQL"

if ($SkipDump -and (Test-Path $dumpFile)) {
    Warn "跳过数据库导出，使用已有 $dumpFile"
} else {
    $mysqldump = Get-Command mysqldump -ErrorAction SilentlyContinue
    if (-not $mysqldump) {
        Warn "未找到 mysqldump 命令。"
        if (Test-Path $dumpFile) {
            Warn "将使用已有 $dumpFile"
        } else {
            Die "无法导出数据库且无历史 01-init.sql。请手动导出 $DbName 到 $dumpFile"
        }
    } else {
        Write-Host "正在导出 $DbName ..."
        # 导出结构+数据，排除会冲突的会话/日志大表可选；这里全量导出
        $env:MYSQL_PWD = $DbPass
        mysqldump -u$DbUser --default-character-set=utf8mb4 `
            --single-transaction --routines --triggers --events `
            --column-statistics=0 `
            $DbName 2>$null | Out-File -FilePath $dumpFile -Encoding utf8
        $env:MYSQL_PWD = ""
        if (-not (Test-Path $dumpFile)) { Die "数据库导出失败。" }
        $size = [math]::Round((Get-Item $dumpFile).Length/1KB,1)
        Ok "数据库已导出：$dumpFile（${size} KB）"
    }
}

# ---- 3. 构建镜像 ----
if (-not $NoBuild) {
    Step "3/6 构建后端/前端镜像（首次较慢）"
    Push-Location $DeployDir
    docker compose build
    if ($LASTEXITCODE -ne 0) { Pop-Location; Die "镜像构建失败。" }
    Pop-Location
    Ok "镜像构建完成"
} else {
    Step "3/6 跳过镜像构建（-NoBuild）"
}

# ---- 4. 启动服务 ----
Step "4/6 启动全部容器"
Push-Location $DeployDir
docker compose up -d
if ($LASTEXITCODE -ne 0) { Pop-Location; Die "容器启动失败。" }
Pop-Location
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
Write-Host "等待 后端(8080)，Spring Boot 启动约需 30-60 秒..."
if (Test-Tcp "localhost" 8080 150) { Ok "后端端口 8080 已就绪" } else { Warn "后端 8080 等待超时，请用 docker logs ai12348-backend 查看" }
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
Write-Host "  后端接口       : http://localhost:8080" -ForegroundColor Gray
Write-Host "  Druid 监控     : http://localhost:8080/druid (ruoyi/123456)" -ForegroundColor Gray
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
