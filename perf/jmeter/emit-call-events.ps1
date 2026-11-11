# P7 场景B 补充：stream:call-event XADD 生产者（PowerShell，无需 redis-cli）
#
# 用途：真实驱动 Redis Stream → CallEventDispatcher → CallEventBus → CallEventProcessor 链路，
#       弥补 JMeter 场景B 只覆盖 HTTP 回调入口（/lawyers/call/event）的缺口。
#
# 用法：
#   .\emit-call-events.ps1 -DryRun                                  # 干跑：只生成并校验消息体，不连 Redis
#   .\emit-call-events.ps1 -EventsPerMin 1000 -DurationSeconds 600
#   .\emit-call-events.ps1 -EventsPerMin 3000 -DurationSeconds 600 -RedisHost 10.0.0.6 -RedisPassword ***
#
# 协议：直接实现 RESP（TCP），只发 AUTH / SELECT / PING / XLEN / XADD，不依赖 redis-cli 与本机 Redis 安装。
#
# 消息契约（与实现一致，勿改字段名）：
#   key   = stream:call-event（QueueNames.CALL_EVENT + stream: 前缀）
#   field = payload（StreamQueueService.PAYLOAD_FIELD）
#   value = {"eventType":"PBX_EVENT","callEvent":{eventId,source,eventName,sessionId,...}}
#   消费组 ai-lawyers 由应用启动时创建，本脚本只生产，不建组、不裁剪（不加 MAXLEN）。
#
# 覆盖口径：
#   - 合成事件 sessionId=perf-<RunId>-<seq>；未预置 ai_call_dial_log 时，处理器在
#     CallDispatchServiceImpl.onCallEvent 的 selectByCallUuid 早退（只压 Stream 消费与处理器分发吞吐）；
#   - 需覆盖回写路径时，按同一规则预置 call_uuid（见容量基线文档 §5.2），并让序号范围与压测事件数对齐。
#   - ANSWERED/RINGING 命中未终态拨号日志时会再投递一条 UPDATE_DIAL_LOG（同 Stream，链路自然放大）。
#
# 参数说明：
#   -EventMix   事件配比，逗号分隔 name:weight，可用 RINGING/ANSWERED/HANGUP/DTMF/RECORD_STOP
#   -RampSeconds 线性升速时长，0 表示全速直发
#   -RunId       会话号段标识，默认启动时刻 HHmmss，可与预置 SQL 的 call_uuid 规则对应

param(
    [string]$RedisHost = 'localhost',
    [int]$RedisPort = 6379,
    [string]$RedisPassword = '',
    [int]$DB = 0,
    [string]$Stream = 'stream:call-event',
    [ValidateRange(1, 60000)]
    [int]$EventsPerMin = 1000,
    [ValidateRange(1, 86400)]
    [int]$DurationSeconds = 600,
    [ValidateRange(0, 3600)]
    [int]$RampSeconds = 60,
    [string]$EventMix = 'ANSWERED:70,HANGUP:30',
    [string]$UuidPrefix = 'perf',
    [string]$RunId = (Get-Date -Format 'HHmmss'),
    [int]$ConnectTimeoutMs = 5000,
    [switch]$DryRun,
    [switch]$Verify
)

$ErrorActionPreference = 'Stop'

# ---------- 事件配比解析 ----------

$allowed = @('RINGING', 'ANSWERED', 'HANGUP', 'DTMF', 'RECORD_STOP')
$mix = @()
$totalWeight = 0
foreach ($part in $EventMix.Split(','))
{
    $kv = $part.Split(':')
    if ($kv.Count -ne 2)
    {
        throw "EventMix 片段格式应为 name:weight，实际为 '$part'"
    }
    $name = $kv[0].Trim().ToUpper()
    $weight = [int]$kv[1].Trim()
    if ($allowed -notcontains $name)
    {
        throw "不支持的事件名 '$name'，可选：$($allowed -join '/')"
    }
    if ($weight -le 0)
    {
        throw "事件权重必须为正整数：$part"
    }
    $mix += [pscustomobject]@{ Name = $name; Weight = $weight }
    $totalWeight += $weight
}
if ($totalWeight -le 0)
{
    throw 'EventMix 权重合计必须大于 0'
}

function Pick-Event
{
    $r = Get-Random -Minimum 1 -Maximum ($totalWeight + 1)
    $acc = 0
    foreach ($m in $mix)
    {
        $acc += $m.Weight
        if ($r -le $acc)
        {
            return $m.Name
        }
    }
    return $mix[$mix.Count - 1].Name
}

# ---------- 消息体构造（字段名与 CallEventPayload / CallEvent 一一对应） ----------

function New-EventJson([string]$eventName, [long]$seq)
{
    $uuid = "$UuidPrefix-$RunId-$seq"
    $now = [long](([DateTime]::UtcNow - [DateTime]'1970-01-01T00:00:00Z').TotalMilliseconds)
    $callEvent = [ordered]@{
        eventId   = [guid]::NewGuid().ToString()
        source    = "XADD:$RedisHost"
        eventName = $eventName
        sessionId = $uuid
        channel   = $uuid
        caller    = ('138{0:D8}' -f (Get-Random -Minimum 10000000 -Maximum 99999999))
        callee    = '4008001234'
        timestamp = $now
        payload   = [ordered]@{
            rawEventName = "PERF_$eventName"
            hangupCause  = 'NORMAL_CLEARING'
            talkDuration = (Get-Random -Minimum 5 -Maximum 120)
        }
    }
    $message = [ordered]@{
        eventType = 'PBX_EVENT'
        callEvent = $callEvent
    }
    return ($message | ConvertTo-Json -Compress -Depth 6)
}

function Assert-EventJson([string]$json)
{
    $obj = $json | ConvertFrom-Json
    if ($obj.eventType -ne 'PBX_EVENT') { throw "eventType 校验失败：$($obj.eventType)" }
    if ($null -eq $obj.callEvent) { throw 'callEvent 缺失' }
    if ($obj.callEvent.eventName -notin $allowed) { throw "callEvent.eventName 校验失败：$($obj.callEvent.eventName)" }
    if ([string]::IsNullOrEmpty($obj.callEvent.sessionId)) { throw 'callEvent.sessionId 缺失' }
    if ([string]::IsNullOrEmpty($obj.callEvent.eventId)) { throw 'callEvent.eventId 缺失' }
    if ($null -eq $obj.callEvent.timestamp -or [long]$obj.callEvent.timestamp -le 0)
    {
        throw 'callEvent.timestamp 缺失或非法'
    }
    return $obj
}

# ---------- RESP 客户端（最小实现） ----------

function Open-Redis
{
    $client = New-Object System.Net.Sockets.TcpClient
    $client.NoDelay = $true
    try
    {
        $iar = $client.BeginConnect($RedisHost, $RedisPort, $null, $null)
        if (-not $iar.AsyncWaitHandle.WaitOne($ConnectTimeoutMs))
        {
            throw "连接超时（${ConnectTimeoutMs}ms）"
        }
        $client.EndConnect($iar)
    }
    catch
    {
        $client.Close()
        $msg = $_.Exception.Message
        if ($null -ne $_.Exception.InnerException)
        {
            $msg = $_.Exception.InnerException.Message
        }
        throw "连接 Redis ${RedisHost}:${RedisPort} 失败：$msg"
    }
    $netStream = $client.GetStream()
    $netStream.ReadTimeout = 15000
    $netStream.WriteTimeout = 15000
    $reader = New-Object System.IO.StreamReader($netStream, [System.Text.Encoding]::UTF8, $false)
    return @{ Client = $client; Stream = $netStream; Reader = $reader }
}

function Read-RedisReply($conn)
{
    $line = $conn.Reader.ReadLine()
    if ($null -eq $line)
    {
        throw 'Redis 连接已断开（读取回复为空）'
    }
    $prefix = $line.Substring(0, 1)
    $body = $line.Substring(1)
    switch ($prefix)
    {
        '+' { return $body }
        '-' { throw "Redis 返回错误：$body" }
        ':' { return [long]$body }
        '$'
        {
            if ([int]$body -eq -1)
            {
                return $null
            }
            # 本脚本只消费单行 bulk 回复（XADD 的消息 ID、PING 的 PONG）
            return $conn.Reader.ReadLine()
        }
        default { throw "未支持的 RESP 回复前缀 '$prefix'：$line" }
    }
}

function Send-RedisCommand($conn, [string[]]$parts)
{
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append("*$($parts.Count)`r`n")
    foreach ($p in $parts)
    {
        $len = [System.Text.Encoding]::UTF8.GetByteCount($p)
        [void]$sb.Append("`$$len`r`n$p`r`n")
    }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($sb.ToString())
    $conn.Stream.Write($bytes, 0, $bytes.Length)
    $conn.Stream.Flush()
    return (Read-RedisReply $conn)
}

# ---------- 干跑：不连 Redis，校验消息体与速率口径 ----------

$planned = [long][Math]::Floor($EventsPerMin * $DurationSeconds / 60.0)
$mixText = ($mix | ForEach-Object { "$($_.Name)*$($_.Weight)" }) -join ','
Write-Host "== stream:call-event XADD 生产者 ==" -ForegroundColor Cyan
Write-Host ("Stream={0}  目标速率={1}/min  时长={2}s  计划事件数≈{3}  事件配比={4}" -f `
        $Stream, $EventsPerMin, $DurationSeconds, $planned, $mixText)

if ($DryRun)
{
    $sample1 = New-EventJson 'ANSWERED' 1
    $sample2 = New-EventJson 'HANGUP' 2
    $null = Assert-EventJson $sample1
    $null = Assert-EventJson $sample2
    if ((New-EventJson 'ANSWERED' 3 | ConvertFrom-Json).callEvent.sessionId -eq $sample1.callEvent.sessionId)
    {
        throw 'sessionId 生成校验失败（不同序号必须产生不同会话号）'
    }
    Write-Host "`n[干跑] 消息体校验通过（eventType/callEvent/eventName/sessionId/eventId/timestamp）" -ForegroundColor Green
    Write-Host "[干跑] 字段名 payload，bulk 字节数=$([System.Text.Encoding]::UTF8.GetByteCount($sample1))"
    Write-Host "[干跑] 会话号段：$UuidPrefix-$RunId-1 .. $UuidPrefix-$RunId-$planned"
    Write-Host "[干跑] 示例消息（缩进展示，实际发送为单行压缩 JSON）：" -ForegroundColor DarkGray
    Write-Host ($sample1 | ConvertFrom-Json | ConvertTo-Json -Depth 6)
    Write-Host "[干跑] 未连接 Redis、未发送任何消息。" -ForegroundColor Yellow
    exit 0
}

# ---------- 真实发送 ----------

$conn = $null
$connectSw = [System.Diagnostics.Stopwatch]::StartNew()
try
{
    $conn = Open-Redis
    if ($RedisPassword)
    {
        $null = Send-RedisCommand $conn @('AUTH', $RedisPassword)
    }
    if ($DB -ne 0)
    {
        $null = Send-RedisCommand $conn @('SELECT', [string]$DB)
    }
    $pong = Send-RedisCommand $conn @('PING')
    if ($pong -ne 'PONG')
    {
        throw "PING 回复异常：$pong"
    }

    $before = Send-RedisCommand $conn @('XLEN', $Stream)
    Write-Host ("已连接 {0}:{1} db={2}，PING 通过，{3} 当前长度={4}（连接耗时 {5}ms）" -f `
            $RedisHost, $RedisPort, $DB, $Stream, $before, $connectSw.ElapsedMilliseconds) -ForegroundColor Green

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $sent = 0
    $failed = 0
    $consecutiveFail = 0
    $seq = 0
    $nextSend = 0.0
    $lastReport = 0.0

    while ($sw.Elapsed.TotalSeconds -lt $DurationSeconds)
    {
        $elapsed = $sw.Elapsed.TotalSeconds
        if ($nextSend -gt $elapsed)
        {
            $waitMs = [int][Math]::Min(50, [Math]::Ceiling(($nextSend - $elapsed) * 1000))
            if ($waitMs -gt 0)
            {
                [System.Threading.Thread]::Sleep($waitMs)
            }
            continue
        }

        # 升速期按比例放宽发送间隔（最低 5% 速率，避免 ramp 段近似停发）
        $ramp = 1.0
        if ($RampSeconds -gt 0 -and $elapsed -lt $RampSeconds)
        {
            $ramp = [Math]::Max(0.05, $elapsed / $RampSeconds)
        }
        $ratePerSec = [Math]::Max(1.0, ($EventsPerMin / 60.0) * $ramp)

        $seq++
        $eventName = Pick-Event
        $json = New-EventJson $eventName $seq
        try
        {
            $null = Send-RedisCommand $conn @('XADD', $Stream, 'payload', $json)
            $sent++
            $consecutiveFail = 0
        }
        catch
        {
            $failed++
            $consecutiveFail++
            Write-Warning "XADD 失败（第 $failed 条）：$($_.Exception.Message)"
            if ($consecutiveFail -ge 3)
            {
                throw '连续 3 次 XADD 失败，终止生产（请检查 Redis 可用性/认证/内存）'
            }
        }

        $nextSend = $elapsed + (1.0 / $ratePerSec)
        if ($nextSend -lt $elapsed)
        {
            $nextSend = $elapsed
        }

        if (($elapsed - $lastReport) -ge 10)
        {
            $lastReport = $elapsed
            Write-Host ("  [{0,5:N0}s] 已发送={1} 失败={2} 实际速率={3:N0}/min" -f `
                    $elapsed, $sent, $failed, ($sent / [Math]::Max(0.001, $elapsed) * 60))
        }
    }

    $wall = [Math]::Max(0.001, $sw.Elapsed.TotalSeconds)
    $after = Send-RedisCommand $conn @('XLEN', $Stream)
    $delta = if (($null -eq $before) -or ($null -eq $after)) { $null } else { [long]$after - [long]$before }

    Write-Host "`n===== 生产汇总 =====" -ForegroundColor Green
    Write-Host ("发送成功={0}  失败={1}  时长={2:N0}s  实际速率={3:N0}/min（目标 {4}/min）" -f `
            $sent, $failed, $wall, ($sent / $wall * 60), $EventsPerMin)
    Write-Host ("{0} 长度 {1} -> {2}（净增量 {3}；消费组 ai-lawyers 在线时增量应显著小于成功数）" -f `
            $Stream, $before, $after, $delta)
    if ($Verify)
    {
        Write-Host "Lag 核验（压测结束后 5 分钟内应清零）：" -ForegroundColor Cyan
        Write-Host "  redis-cli -h $RedisHost -p $RedisPort XINFO GROUPS $Stream"
        Write-Host "  redis-cli -h $RedisHost -p $RedisPort XPENDING $Stream ai-lawyers"
        Write-Host "  或应用 /actuator/prometheus 看 HotlineMetrics 队列积压指标"
    }
    if ($failed -gt 0)
    {
        Write-Host "存在发送失败，生产者不算通过。" -ForegroundColor Red
        exit 1
    }
    Write-Host "生产完成。" -ForegroundColor Green
}
finally
{
    if ($null -ne $conn -and $null -ne $conn.Client)
    {
        $conn.Client.Close()
    }
}
