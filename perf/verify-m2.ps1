# perf\verify-m2.ps1 -- M2 缓存验收（只读脚本，不改任何业务数据）
#
# 用法（在项目根目录）：
#   $env:REDIS_PASSWORD = "你的Redis密码"
#   $env:MYSQL_PWD      = "你的MySQL密码"
#   powershell -ExecutionPolicy Bypass -File perf\verify-m2.ps1
#
# 检查 6 件事：
#   1. health 里 redis 是不是 UP
#   2. 未命中时每请求平均 SQL 条数
#   3. 命中时每请求平均 SQL 条数（期望接近 0）
#   4. 两次响应内容是否一致
#   5. Redis 的 key / type / TTL / 大小
#   6. 缓存 hit / miss 指标
#
# 不需要 redis-cli：用 .NET 原生 TCP 直接说 Redis 协议（RESP）。
$ErrorActionPreference = 'Continue'

# 本机装了代理软件（127.0.0.1:10808）。Invoke-RestMethod 默认走系统代理，
# 会把 localhost 的请求也转发出去而失败，这里显式禁用。
[System.Net.WebRequest]::DefaultWebProxy = [System.Net.GlobalProxySelection]::GetEmptyWebProxy()

$Base      = 'http://localhost:8080'
$RedisHost = if ($env:REDIS_HOST) { $env:REDIS_HOST } else { '192.168.2.51' }
$RedisPort = if ($env:REDIS_PORT) { [int]$env:REDIS_PORT } else { 6379 }
$RedisPwd  = $env:REDIS_PASSWORD
$TestId    = if ($env:TEST_ARTICLE_ID) { $env:TEST_ARTICLE_ID } else { '49170' }
$Key       = "blog:v1:article:detail:$TestId"
$Sample    = if ($env:SAMPLE_REQUESTS) { [int]$env:SAMPLE_REQUESTS } else { 30 }

function Write-Step($text) { Write-Host ""; Write-Host "=== $text ===" -ForegroundColor Cyan }

# ---------------------------------------------------------------- Redis 客户端
# 用 script 作用域保存连接，避免嵌套函数的变量作用域问题
$script:rc = $nu
$script:rw = $null

function Open-Redis {
    $script:rc = New-Object System.Net.Sockets.TcpClient
    $script:rc.ReceiveTimeout = 4000
    $script:rc.Connect($RedisHost, $RedisPort)
    $ns = $script:rc.GetStream()
    $script:rw = New-Object System.IO.StreamReader($ns, [Text.Encoding]::UTF8)
    $script:ww = New-Object System.IO.StreamWriter($ns, (New-Object Text.UTF8Encoding($false)))
    $script:ww.NewLine = "`r`n"
    $script:ww.AutoFlush = $true
    if ($RedisPwd) {
        $script:ww.WriteLine("AUTH $RedisPwd")
        $null = $script:rw.ReadLine()
    }
}

function Close-Redis {
    if ($script:rc) { try { $script:rc.Close() } catch {} }
    $script:rc = $null
    $script:rw = $null
    $script:ww = $null
}

# 支持：简单字符串 / 整数 / bulk string。够用于 TYPE、TTL、STRLEN、DEL、PING
function Send-Redis([string]$command) {
    $script:ww.WriteLine($command)
    $line = $script:rw.ReadLine()
    if ($line -eq $null) { return $null }
    if ($line.StartsWith('$')) {
        $len = [int]$line.Substring(1)
        if ($len -lt 0) { return $null }
        $buf = New-Object char[] $len
        $read = 0
        while ($read -lt $len) { $read += $script:rw.Read($buf, $read, $len - $read) }
        $null = $script:rw.ReadLine()
        return -join $buf
    }
    if ($line.StartsWith(':')) { return [long]$line.Substring(1) }
    return $line
}

# ---------------------------------------------------------------- 工具函数
$MysqlPwd = $env:MYSQL_PWD
$canQueryDb = [bool]$MysqlPwd

function Get-Questions {
    if (-not $canQueryDb) { return -1 }
    $out = & mysql -uroot -N -e "SHOW GLOBAL STATUS LIKE 'Questions';" 2>$null
    if (-not $out) { return -1 }
    return [long](($out | Select-Object -First 1) -replace 'Questions\s+', '')
}

# 批量请求 n 次，返回 [耗时ms, SQL条数]。
# 为什么批量：单次请求加上 mysql CLI 自身的语句，噪声比信号还大
function Measure-Requests([int]$n) {
    $q1 = Get-Questions
    Start-Sleep -Milliseconds 150
    $q1 = Get-Questions
    $sw = [Diagnostics.Stopwatch]::StartNew()
    for ($i = 0; $i -lt $n; $i++) {
        $null = Invoke-RestMethod "$Base/articles/$TestId" -TimeoutSec 10
    }
    $sw.Stop()
    Start-Sleep -Milliseconds 150
    $q2 = Get-Questions
    $sql = if ($q1 -ge 0 -and $q2 -ge 0) { $q2 - $q1 } else { -1 }
    return @{ Ms = [math]::Round($sw.ElapsedMilliseconds / $n, 1); Sql = $sql; PerReq = if ($sql -ge 0) { [math]::Round($sql / $n, 2) } else { -1 } }
}

# ---------------------------------------------------------------- 1. health
Write-Step "1. 健康检查"
try {
    $health = Invoke-RestMethod "$Base/actuator/health" -TimeoutSec 5
} catch {
    Write-Host "  [FAIL] 应用没起来：$Base" -ForegroundColor Red
    Write-Host ("         " + $_.Exception.Message)
    exit 1
}
Write-Host ("  总体  : " + $health.status)
Write-Host ("  redis : " + $health.components.redis.status + "   version=" + $health.components.redis.details.version)
if ($health.components.redis.status -ne 'UP') {
    Write-Host "  [FAIL] Redis 不是 UP，测出来的是降级路径，不是缓存路径" -ForegroundColor Red
    Write-Host "         启动应用时要设 REDIS_PASSWORD（yml 里是 `${REDIS_PASSWORD:}，空值连不上）"
    exit 1
}

# --------------------------------------------------- 清缓存，从冷态开始
Write-Step "预热准备：删掉这个 key，确保第 1 次一定未命中"
$redisOk = $false
if ($RedisPwd) {
    try {
        Open-Redis
        $del = Send-Redis "DEL $Key"
        Write-Host "  已删除 $Key（返回 $del）"
        $redisOk = $true
    } catch {
        Write-Host ("  [WARN] 直连 Redis 失败：" + $_.Exception.Message) -ForegroundColor Yellow
        Close-Redis
    }
} else {
    Write-Host "  跳过（没设 REDIS_PASSWORD）" -ForegroundColor Yellow
}

# --------------------------------------------------- 2. 未命中
Write-Step "2. 未命中：连续 $Sample 次请求（第 1 次回填缓存，其余命中）"
$miss = Measure-Requests $Sample
Write-Host ("  平均耗时: " + $miss.Ms + " ms/次    SQL 总数: " + $miss.Sql + "    平均: " + $miss.PerReq + " 条/次")
Write-Host "  说明: 只有第 1 次未命中打 5 条 SQL，所以平均值应该是 5/N 左右"

# --------------------------------------------------- 3. 命中
Write-Step "3. 命中：再连续 $Sample 次请求（应全部命中）"
$hit = Measure-Requests $Sample
Write-Host ("  平均耗时: " + $hit.Ms + " ms/次    SQL 总数: " + $hit.Sql + "    平均: " + $hit.PerReq + " 条/次")

# 单次请求的未命中断言（用 1 次冷请求单独测，确认 5 条 SQL 这条路径还在）
Write-Step "3b. 冷启动单次验证（删 key 后再请求 1 次，应打 5 条 SQL）"
$singleSql = -1
if ($redisOk) {
    $null = Send-Redis "DEL $Key"
    Start-Sleep -Milliseconds 150
    $q1 = Get-Questions
    $null = Invoke-RestMethod "$Base/articles/$TestId" -TimeoutSec 10
    Start-Sleep -Milliseconds 150
    $q2 = Get-Questions
    if ($q1 -ge 0 -and $q2 -ge 0) { $singleSql = $q2 - $q1 }
    Write-Host ("  SQL 条数: " + $singleSql + "（含 MySQL 内部语句，5~7 都正常）")
}

# --------------------------------------------------- 4. 内容一致性
Write-Step "4. 两次响应内容是否一致"
$r1 = Invoke-RestMethod "$Base/articles/$TestId" -TimeoutSec 10
$r2 = Invoke-RestMethod "$Base/articles/$TestId" -TimeoutSec 10
$j1 = $r1.data | ConvertTo-Json -Depth 8 -Compress
$j2 = $r2.data | ConvertTo-Json -Depth 8 -Compress
$same = ($j1 -eq $j2)
if ($same) {
    Write-Host "  [OK] 完全一致（缓存反序列化正确）" -ForegroundColor Green
} else {
    Write-Host ("  [FAIL] 不一致！长度 " + $j1.Length + " vs " + $j2.Length) -ForegroundColor Red
}
Write-Host ("  content 存在: " + [bool]$r2.data.content + "   正文长度: " + ($r2.data.content | Measure-Object -Character).Characters + " 字符")
Write-Host ("  tags 数量: " + @($r2.data.tags).Count)

# --------------------------------------------------- 5. key 与 TTL
Write-Step "5. Redis key / type / TTL / 大小"
if ($redisOk) {
    $typ = Send-Redis "TYPE $Key"
    $ttl = Send-Redis "TTL $Key"
    $len = Send-Redis "STRLEN $Key"
    Write-Host "  key  : $Key"
    Write-Host "  type : $typ     ttl : $ttl 秒     大小 : $len 字节"
    if ($ttl -ge 1800 -and $ttl -le 2100) {
        Write-Host "  [OK] TTL 在 1800~2100 秒（30 分钟 + 0~300 秒抖动）" -ForegroundColor Green
    } else {
        Write-Host "  [WARN] TTL 不在预期区间" -ForegroundColor Yellow
    }
    Close-Redis
} else {
    Write-Host "  跳过" -ForegroundColor Yellow
}

# --------------------------------------------------- 6. 命中指标
Write-Step "6. 缓存命中指标"
try {
    $all  = Invoke-RestMethod "$Base/actuator/metrics/blog.cache.requests" -TimeoutSec 5
    $hitM  = Invoke-RestMethod "$Base/actuator/metrics/blog.cache.requests?tag=result:hit"  -TimeoutSec 5
    $missM = Invoke-RestMethod "$Base/actuator/metrics/blog.cache.requests?tag=result:miss" -TimeoutSec 5
    $h = ($hitM.measurements  | Where-Object { $_.statistic -eq 'COUNT' }).value
    $m = ($missM.measurements | Where-Object { $_.statistic -eq 'COUNT' }).value
    $t = ($all.measurements   | Where-Object { $_.statistic -eq 'COUNT' }).value
    Write-Host ("  hit = " + $h + "    miss = " + $m + "    合计 = " + $t)
    if ($t -gt 0) {
        Write-Host ("  命中率 = " + [math]::Round(100 * $h / $t, 1) + "%")
    }
} catch {
    Write-Host "  取不到指标（可能还没发生过缓存访问）" -ForegroundColor Yellow
}

# --------------------------------------------------- 汇总
Write-Step "汇总"
Write-Host ("  未命中批（" + $Sample + " 次）: " + $miss.Ms + " ms/次, " + $miss.PerReq + " 条 SQL")
Write-Host ("  命中批  （" + $Sample + " 次）: " + $hit.Ms + " ms/次, " + $hit.PerReq + " 条 SQL")
Write-Host ("  单次冷请求 SQL 条数: " + $singleSql)
Write-Host ("  响应内容一致: " + $same)
$pass = $same -and ($hit.PerReq -ge 0) -and ($hit.PerReq -le 0.3)
if ($pass) {
    Write-Host "  结论: PASS（命中时 SQL 平均 <= 0.3 条/次）" -ForegroundColor Green
} else {
    Write-Host "  结论: 需人工确认" -ForegroundColor Yellow
}
Write-Host ""
Write-Host "  参考基线: M1 时详情接口是 5.00 条 SQL / 次"
