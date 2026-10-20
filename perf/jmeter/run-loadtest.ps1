# P7 压测常态化回归脚本（PowerShell）
# 用法：
#   .\run-loadtest.ps1 -Scenario a -Tier 1                 # 场景A 基线档
#   .\run-loadtest.ps1 -Scenario b -Tier 2 -HostUrl 10.0.0.5 -Port 8080
#   .\run-loadtest.ps1 -Scenario all -Tier 3               # 三场景全跑峰值档
# 依赖：JMeter 5.4+ 在 PATH（或 -JMeterPath 指定 bin/jmeter.bat）
param(
    [ValidateSet('a','b','c','all')]
    [string]$Scenario = 'a',
    [ValidateRange(1,3)]
    [int]$Tier = 1,
    [string]$HostUrl = 'localhost',
    [int]$Port = 8080,
    [string]$User = 'admin',
    [string]$Password = 'admin123',
    [string]$JMeterPath = 'jmeter'
)

$ErrorActionPreference = 'Stop'
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$outRoot = Join-Path $dir "results\$stamp"

# 三档容量参数（详见 容量基线与压测常态化_P7_20261020.md）
$tiers = @{
    1 = @{ tierA = 100; tierB = 20;  readersB = 10; tierC = 10; callsPerMin = 1000;  inspectionsPerMin = 100; name = 'T1基线' }
    2 = @{ tierA = 300; tierB = 50;  readersB = 20; tierC = 20; callsPerMin = 3000;  inspectionsPerMin = 300; name = 'T2目标' }
    3 = @{ tierA = 500; tierB = 100; readersB = 30; tierC = 30; callsPerMin = 6000;  inspectionsPerMin = 600; name = 'T3峰值' }
}
$t = $tiers[$Tier]
$duration = 600   # 单场景压测时长（秒）
$ramp = 60

$scenarios = if ($Scenario -eq 'all') { @('a','b','c') } else { @($Scenario) }
$summary = @()

foreach ($s in $scenarios) {
    $jmx = Join-Path $dir "scenario-$s-$(if($s -eq 'a'){'agents'}elseif($s -eq 'b'){'calls'}else{'quality'}).jmx"
    $out = Join-Path $outRoot "scenario-$s"
    New-Item -ItemType Directory -Force -Path $out | Out-Null

    $jmeterArgs = @(
        '-n', '-t', $jmx,
        '-l', (Join-Path $out 'result.jtl'),
        '-e', '-o', (Join-Path $out 'report'),
        "-Jhost=$HostUrl", "-Jport=$Port", "-Juser=$User", "-Jpassword=$Password",
        "-Jduration=$duration", "-JrampA=$ramp", "-JrampB=$ramp", "-JrampC=$ramp",
        "-JtierA=$($t.tierA)", "-JtierB=$($t.tierB)", "-JtierC=$($t.tierC)",
        "-JreadersB=$($t.readersB)",
        "-JcallsPerMin=$($t.callsPerMin)", "-JinspectionsPerMin=$($t.inspectionsPerMin)"
    )
    Write-Host "== [T$Tier/$($t.name)] 运行场景 $s : $jmx" -ForegroundColor Cyan
    & $JMeterPath @jmeterArgs
    if ($LASTEXITCODE -ne 0) { throw "JMeter 场景 $s 退出码 $LASTEXITCODE" }

    # 从 jtl 汇总关键指标
    $jtl = Import-Csv (Join-Path $out 'result.jtl')
    $total = $jtl.Count
    $errs = ($jtl | Where-Object success -ne 'true').Count
    $avg = [math]::Round((($jtl | Measure-Object elapsed -Average).Average), 1)
    $p95 = ($jtl | ForEach-Object { [int]$_.elapsed } | Sort-Object)[[int][math]::Floor($total * 0.95)]
    $tpm = [math]::Round($total / ($duration / 60.0), 1)
    $summary += [pscustomobject]@{ 场景 = $s; 档位 = "T$Tier"; 请求数 = $total; 错误数 = $errs
                                   平均RTms = $avg; P95ms = $p95; 每分钟事务 = $tpm
                                   报告 = (Join-Path $out 'report\index.html') }
}

Write-Host "`n===== 容量基线汇总（T$Tier $($t.name)）=====" -ForegroundColor Green
$summary | Format-Table -AutoSize
$summary | ConvertTo-Json -Depth 3 | Set-Content (Join-Path $outRoot 'baseline-summary.json') -Encoding UTF8
Write-Host "结果与 HTML 报告：$outRoot"
Write-Host "通过门槛：错误率 < 1% 且 P95 < 2000ms（场景B事件接口 P95 < 500ms），不达标需回查容量基线文档。"
