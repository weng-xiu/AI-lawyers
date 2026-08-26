# =====================================================================
# FreeSWITCH 律师坐席分机（1003-1006）一键部署（Windows / Docker）
# 适用于已在运行的 freeswitch 容器（docker-compose 部署时已用卷挂载，无需本脚本）
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File freeswitch-conf\deploy-agent-extensions.ps1
# =====================================================================
$ErrorActionPreference = "Stop"

$ContainerName = "freeswitch"
$FsCli = "/usr/local/freeswitch/bin/fs_cli"
$ConfDirInContainer = "/usr/local/freeswitch/conf/directory/default"
$Extensions = @(
    @{ Ext = "1003"; Name = "张律师(婚姻)"; Gonghao = "102" },
    @{ Ext = "1004"; Name = "李律师(婚姻)"; Gonghao = "103" },
    @{ Ext = "1005"; Name = "王律师(合同)"; Gonghao = "104" },
    @{ Ext = "1006"; Name = "赵律师(合同)"; Gonghao = "105" }
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$LocalDefaultDir = Join-Path $ScriptDir "directory\default"

function Step($m){ Write-Host "`n[*] $m" -ForegroundColor Cyan }
function Ok($m){ Write-Host "[OK] $m" -ForegroundColor Green }
function Die($m){ Write-Host "[X] $m" -ForegroundColor Red; exit 1 }

Step "检查容器 [$ContainerName] 运行状态..."
$running = docker ps --filter "name=^/$ContainerName$" --filter "status=running" --format "{{.Names}}" 2>$null
if ($running -ne $ContainerName) {
    # 兼容 compose 命名的容器
    $running = docker ps --filter "name=freeswitch" --filter "status=running" --format "{{.Names}}" 2>$null | Select-Object -First 1
    if (-not $running) { Die "未找到运行中的 freeswitch 容器。" }
    $ContainerName = $running
}
Ok "容器 [$ContainerName] 正在运行"

Step "拷贝分机配置到容器..."
foreach ($e in $Extensions) {
    $xml = "$($e.Ext).xml"
    $localPath = Join-Path $LocalDefaultDir $xml
    if (-not (Test-Path $localPath)) { Die "缺少配置文件：$localPath" }
    docker cp $localPath "${ContainerName}:$ConfDirInContainer/$xml" | Out-Null
    Write-Host "    $xml -> $($e.Name)/工号$($e.Gonghao)"
}
Ok "4 个分机配置已拷贝"

Step "reloadxml 热加载..."
docker exec $ContainerName $FsCli -x "reloadxml"
docker exec $ContainerName $FsCli -x "sofia profile internal restart reloadxml" 2>$null
Start-Sleep -Seconds 2

Step "验证分机..."
foreach ($e in $Extensions) {
    $out = docker exec $ContainerName $FsCli -x "user_exists $($e.Ext)" 2>$null
    if ($out -match "true|exist") { Ok "分机 $($e.Ext)（$($e.Name)）已加载" }
    else { Write-Host "[!] 分机 $($e.Ext) 请进 fs_cli 手动确认：user_exists $($e.Ext)" -ForegroundColor Yellow }
}

Write-Host "`n[OK] 部署完成。律师签入后软电话以对应分机（密码 1234）注册。" -ForegroundColor Green
