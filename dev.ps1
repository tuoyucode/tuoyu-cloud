# TuoYu-Cloud 一键开发环境，与同目录的 dev（bash）命令相同。用法：.\dev.ps1 help
# 兼容 Windows PowerShell 5.1 与 PowerShell 7。执行策略不允许运行脚本时：powershell -ExecutionPolicy Bypass -File .\dev.ps1 help
# 本文件须保存为带 BOM 的 UTF-8，否则 Windows PowerShell 5.1 会按系统编码读取中文而出错。

$Root = $PSScriptRoot
$ComposeDir = 'deploy/docker'
$MonolithModule = 'tuoyu-boot/tuoyu-boot-all'
$MonolithJar = "$MonolithModule/target/tuoyu-boot-all.jar"

# 没有默认值、缺失时服务拒绝启动的变量
$RequiredVars = @('TUOYU_DB_PASSWORD', 'TUOYU_REDIS_PASSWORD', 'TUOYU_NACOS_PASSWORD', 'TUOYU_JWT_SECRET',
    'TUOYU_API_CRYPTO_PRIVATE_KEY', 'TUOYU_MONITOR_PASSWORD', 'TUOYU_SNAILJOB_TOKEN')
# 只有用 docker compose 部署 Nacos、MinIO 时才需要的变量
$ComposeVars = @('TUOYU_NACOS_AUTH_TOKEN', 'TUOYU_NACOS_IDENTITY_KEY', 'TUOYU_NACOS_IDENTITY_VALUE',
    'TUOYU_MINIO_ACCESS_KEY', 'TUOYU_MINIO_SECRET_KEY')
# 已废弃的变量（换用新的接口加密方案后不再使用）
$ObsoleteVars = @('TUOYU_API_REQUEST_PRIVATE_KEY', 'TUOYU_API_RESPONSE_PUBLIC_KEY')

function Write-Info([string]$Message) { Write-Host $Message }
function Stop-WithError([string]$Message) {
    Write-Host "[错误] $Message" -ForegroundColor Red
    exit 1
}

function Show-Usage {
    @'
TuoYu-Cloud 一键开发环境

用法：.\dev.ps1 <命令> [参数]

命令：
  env [--force]          生成 .env（已存在时只检查；--force 重新生成，会覆盖现有口令），并检查必填项与已废弃的变量
  up                     准备本地开发所需的中间件：
                           有 Docker 时用 compose 启动 MySQL、Redis、Nacos，并初始化 Nacos 账号、导入配置；
                           没有 Docker 时检查本机的 MySQL、Redis、Nacos 能否连接，并给出提示
  down [参数]            停止并删除 compose 启动的容器（保留数据卷；加 -v 同时删除数据卷）
  status                 查看 .env、JDK、Maven、Docker，以及中间件与各服务端口的状态
  monolith [--no-build] [程序参数...]
                         构建并启动单体（需要 JDK 21；构建会运行测试）。--no-build：已有 jar 时跳过构建
  nacos-import [参数]    把 deploy/config/nacos 下的配置导入 Nacos，默认只预览；
                           --apply 导入，--apply --overwrite 覆盖内容不同的已有配置
  compose <core|full|monolith|extras> [docker compose 参数...]
                         用指定的 compose 文件执行 docker compose 命令，例如：
                           .\dev.ps1 compose core up -d --build
                           .\dev.ps1 compose full logs -f tuoyu-system
                           .\dev.ps1 compose extras --profile metrics up -d    （extras = full + 附加组件）
  help                   显示本帮助

说明：
  - 所有命令都在仓库根目录执行，读取仓库根目录的 .env；同名的环境变量优先于 .env；
  - 需要 JDK 的命令优先使用 JAVA_HOME 中的 java，其次使用 PATH 中的 java。
    系统默认 java 不是 21 时，先在当前窗口设置：$env:JAVA_HOME='D:\path\to\jdk-21'；
  - 三套 compose 的说明见 deploy/docker/README.md。
'@ | Write-Host
}

# ---------- .env ----------

# 读取变量：环境变量优先，其次 .env（空值视为未设置）
function Get-EnvValue([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if (-not [string]::IsNullOrEmpty($value)) { return $value }
    if (Test-Path -LiteralPath '.env') {
        $found = $null
        foreach ($line in [IO.File]::ReadAllLines((Join-Path $Root '.env'))) {
            if ($line.StartsWith("$Name=")) { $found = $line.Substring($Name.Length + 1).Trim() }
        }
        if ($null -ne $found) { return $found }
    }
    return ''
}

function Get-EnvFileKeys {
    if (-not (Test-Path -LiteralPath '.env')) { return @() }
    $keys = @()
    foreach ($line in [IO.File]::ReadAllLines((Join-Path $Root '.env'))) {
        if ($line -match '^(TUOYU_[A-Z0-9_]+)=') { $keys += $Matches[1] }
    }
    return $keys | Sort-Object -Unique
}

# ---------- JDK / 工具 ----------

function Get-JavaCommand {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (Test-Path -LiteralPath $candidate) { return $candidate }
        $candidate = Join-Path $env:JAVA_HOME 'bin/java'
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    $command = Get-Command java -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    return $null
}

# 返回 java 主版本号（1.8 记为 8），无法判断时返回 0
function Get-JavaMajor([string]$Java) {
    try {
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $Java
        $psi.Arguments = '-version'
        $psi.RedirectStandardError = $true
        $psi.RedirectStandardOutput = $true
        $psi.UseShellExecute = $false
        $psi.CreateNoWindow = $true
        $process = [Diagnostics.Process]::Start($psi)
        $output = $process.StandardError.ReadToEnd() + $process.StandardOutput.ReadToEnd()
        $process.WaitForExit()
        if ($output -match 'version "1\.(\d+)') { return [int]$Matches[1] }
        if ($output -match 'version "(\d+)') { return [int]$Matches[1] }
    } catch {
    }
    return 0
}

# 要求 JDK 达到指定主版本，返回可用的 java 命令
function Get-RequiredJava([int]$Min) {
    $java = Get-JavaCommand
    if (-not $java) { Stop-WithError '找不到 java。请安装 JDK 21，并设置 JAVA_HOME 或把 java 加入 PATH' }
    $major = Get-JavaMajor $java
    if ($major -lt $Min) {
        $shown = if ($major -gt 0) { "$major" } else { '未知' }
        Stop-WithError "需要 JDK $Min 及以上，当前为 $shown（$java）。请先在当前窗口设置：`$env:JAVA_HOME='D:\path\to\jdk-21'"
    }
    return $java
}

function Get-MavenCommand {
    if (Get-Command mvn -ErrorAction SilentlyContinue) { return 'mvn' }
    return (Join-Path $Root 'mvnw.cmd')
}

function Test-DockerReady {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { return $false }
    & docker info *> $null
    if ($LASTEXITCODE -ne 0) { return $false }
    & docker compose version *> $null
    return ($LASTEXITCODE -eq 0)
}

function Get-ComposeFiles([string]$Name) {
    switch ($Name) {
        'core' { return @('-f', "$ComposeDir/compose-core.yml") }
        'full' { return @('-f', "$ComposeDir/compose-full.yml") }
        'monolith' { return @('-f', "$ComposeDir/compose-monolith.yml") }
        'extras' { return @('-f', "$ComposeDir/compose-full.yml", '-f', "$ComposeDir/compose-extras.yml") }
    }
    return $null
}

# ---------- 网络检查 ----------

function Connect-Tcp([string]$HostName, [int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync($HostName, $Port)
        if ($task.Wait(3000) -and $client.Connected) { return $client }
    } catch {
    }
    $client.Close()
    return $null
}

function Test-TcpPort([string]$HostName, [int]$Port) {
    $client = Connect-Tcp $HostName $Port
    if ($client) { $client.Close(); return $true }
    return $false
}

# 发送请求并读取回复（直到对方关闭连接或超时）
function Invoke-TcpExchange([string]$HostName, [int]$Port, [byte[]]$Request, [int]$MaxBytes = 4096) {
    $client = Connect-Tcp $HostName $Port
    if (-not $client) { return $null }
    try {
        $stream = $client.GetStream()
        $stream.ReadTimeout = 3000
        if ($Request) { $stream.Write($Request, 0, $Request.Length) }
        $buffer = New-Object byte[] $MaxBytes
        $total = 0
        try {
            while ($total -lt $MaxBytes) {
                $read = $stream.Read($buffer, $total, $MaxBytes - $total)
                if ($read -le 0) { break }
                $total += $read
                if ($null -eq $Request) { break }
            }
        } catch {
        }
        $bytes = New-Object byte[] $total
        [Array]::Copy($buffer, $bytes, $total)
        return $bytes
    } finally {
        $client.Close()
    }
}

function Get-DbHost { $v = Get-EnvValue 'TUOYU_DB_HOST'; if ($v) { $v } else { 'localhost' } }
function Get-DbPort {
    $v = Get-EnvValue 'TUOYU_DB_PORT'
    if ($v) { return [int]$v }
    if ((Get-EnvValue 'TUOYU_DB_TYPE') -eq 'postgresql') { return 5432 }
    return 3306
}
function Get-RedisHost { $v = Get-EnvValue 'TUOYU_REDIS_HOST'; if ($v) { $v } else { 'localhost' } }
function Get-RedisPort { $v = Get-EnvValue 'TUOYU_REDIS_PORT'; if ($v) { [int]$v } else { 6379 } }

function Test-Database {
    $dbHost = Get-DbHost
    $port = Get-DbPort
    if ((Get-EnvValue 'TUOYU_DB_TYPE') -eq 'postgresql') {
        if (Test-TcpPort $dbHost $port) { Write-Info "  [可连接] PostgreSQL  ${dbHost}:$port"; return $true }
        Write-Info "  [不可连接] PostgreSQL  ${dbHost}:$port"
        return $false
    }
    # MySQL 连接后先发送握手包，其中带有版本号
    $greeting = Invoke-TcpExchange $dbHost $port $null 128
    if ($null -eq $greeting) { Write-Info "  [不可连接] MySQL  ${dbHost}:$port"; return $false }
    $text = [Text.Encoding]::ASCII.GetString($greeting)
    if ($text -match '(\d+\.\d+\.\d+[^\x00]*)') {
        Write-Info "  [可连接] MySQL $($Matches[1])  ${dbHost}:$port"
    } else {
        Write-Info "  [可连接] MySQL（未读到版本号）  ${dbHost}:$port"
    }
    return $true
}

function Test-Redis {
    $redisHost = Get-RedisHost
    $port = Get-RedisPort
    $password = Get-EnvValue 'TUOYU_REDIS_PASSWORD'
    $request = ''
    if ($password) {
        $length = [Text.Encoding]::UTF8.GetByteCount($password)
        $request += "*2`r`n`$4`r`nAUTH`r`n`$$length`r`n$password`r`n"
    }
    $request += "*1`r`n`$4`r`nPING`r`n*1`r`n`$4`r`nQUIT`r`n"
    $reply = Invoke-TcpExchange $redisHost $port ([Text.Encoding]::UTF8.GetBytes($request))
    if ($null -eq $reply) { Write-Info "  [不可连接] Redis  ${redisHost}:$port"; return $false }
    $text = [Text.Encoding]::UTF8.GetString($reply)
    if ($text -match 'WRONGPASS|NOAUTH|invalid password') {
        Write-Info "  [口令错误] Redis  ${redisHost}:$port（检查 TUOYU_REDIS_PASSWORD）"
        return $false
    }
    if ($text -match 'PONG') { Write-Info "  [可连接] Redis  ${redisHost}:$port"; return $true }
    Write-Info "  [不可连接] Redis  ${redisHost}:$port"
    return $false
}

function Test-Nacos {
    $server = Get-EnvValue 'TUOYU_NACOS_SERVER'
    if (-not $server) { $server = '127.0.0.1:8848' }
    $server = $server -replace '^https?://', ''
    $parts = $server.Split(':')
    $nacosHost = $parts[0]
    $port = if ($parts.Length -gt 1) { [int]$parts[1] } else { 8848 }
    $request = [Text.Encoding]::ASCII.GetBytes("GET /nacos/v1/console/health/readiness HTTP/1.0`r`nHost: $nacosHost`r`n`r`n")
    $reply = Invoke-TcpExchange $nacosHost $port $request
    if ($null -eq $reply) { Write-Info "  [不可连接] Nacos  ${nacosHost}:$port（单体不需要 Nacos）"; return $false }
    $status = ([Text.Encoding]::ASCII.GetString($reply) -split "`r?`n")[0]
    if ($status -match '^HTTP/\S+ 200') { Write-Info "  [可连接] Nacos  ${nacosHost}:$port"; return $true }
    Write-Info "  [未就绪] Nacos  ${nacosHost}:$port（$status）"
    return $false
}

# ---------- 命令 ----------

function Invoke-Env([string[]]$Arguments) {
    $force = $Arguments -contains '--force'
    if (-not (Test-Path -LiteralPath '.env') -or $force) {
        $java = Get-RequiredJava 21
        Write-Info '生成 .env（tools/secrets/GenerateEnv.java）...'
        $toolArgs = @('tools/secrets/GenerateEnv.java')
        if ($force) { $toolArgs += '--force' }
        # 输出直接写到控制台，不能混进函数的返回值
        & $java @toolArgs | Out-Host
        if ($LASTEXITCODE -ne 0) { Stop-WithError '生成 .env 失败' }
    } else {
        Write-Info '.env 已存在，只做检查（重新生成：.\dev.ps1 env --force，会覆盖现有口令）'
    }

    Write-Info ''
    Write-Info '检查 .env：'
    $missing = $false
    foreach ($name in $RequiredVars) {
        if (-not (Get-EnvValue $name)) { Write-Info "  [缺少] $name（必填，缺失时服务拒绝启动）"; $missing = $true }
    }
    foreach ($name in $ComposeVars) {
        if (-not (Get-EnvValue $name)) { Write-Info "  [缺少] $name（用 docker compose 部署 Nacos、MinIO 时必填）" }
    }
    if ((Get-EnvValue 'TUOYU_TENANT_ENABLED') -eq 'true' -and -not (Get-EnvValue 'TUOYU_TENANT_CONTEXT_SECRET')) {
        Write-Info '  [缺少] TUOYU_TENANT_CONTEXT_SECRET（已开启多租户，必填）'
        $missing = $true
    }
    $keys = @(Get-EnvFileKeys)
    foreach ($name in $ObsoleteVars) {
        if ($keys -contains $name) { Write-Info "  [已废弃] $name（接口加密已换用新方案，请从 .env 中删除）" }
    }
    if ((Get-EnvValue 'TUOYU_PROFILES_ACTIVE') -eq 'prod') {
        foreach ($name in @('TUOYU_ACTUATOR_EXPOSURE', 'TUOYU_HEALTH_SHOW_DETAILS', 'TUOYU_API_DOCS_ENABLED', 'TUOYU_GATEWAY_DISCOVERY_LOCATOR')) {
            if (Get-EnvValue $name) { Write-Info "  [注意] $name 是开发便利项，prod 下启动自检会拒绝不安全的值，生产环境请删除" }
        }
    }
    # 未登记在 .env.example 中的变量（多为拼写错误；第三方登录变量按平台统一说明，不逐个登记）
    if (Test-Path -LiteralPath '.env.example') {
        $documented = @()
        foreach ($line in [IO.File]::ReadAllLines((Join-Path $Root '.env.example'))) {
            if ($line -match '^(TUOYU_[A-Z0-9_]+)=') { $documented += $Matches[1] }
        }
        foreach ($name in $keys) {
            if ($name.StartsWith('TUOYU_SOCIAL_') -or ($documented -contains $name) -or ($ObsoleteVars -contains $name)) { continue }
            Write-Info "  [未知] $name 未登记在 .env.example 中，请检查拼写"
        }
    }
    if ($missing) { return $false }
    Write-Info '  必填项齐全'
    return $true
}

function Invoke-Up {
    if (-not (Test-Path -LiteralPath '.env')) {
        Write-Info '未找到 .env，先生成：'
        if (-not (Invoke-Env @())) { exit 1 }
        Write-Info ''
    }
    if (Test-DockerReady) {
        Write-Info '检测到 Docker：启动 MySQL、Redis、Nacos（compose-core.yml 中的中间件），并初始化 Nacos 账号、导入配置...'
        $composeArgs = @('compose', '--env-file', '.env') + (Get-ComposeFiles 'core') + @('up', '-d', 'mysql', 'redis', 'nacos', 'nacos-init')
        & docker @composeArgs
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        Write-Info ''
        Write-Info '中间件已在后台启动（首次启动需要初始化，约 1 ~ 2 分钟），端口只绑定本机：'
        Write-Info '  MySQL 127.0.0.1:3306、Redis 127.0.0.1:6379、Nacos 127.0.0.1:8848（控制台 http://127.0.0.1:8848/nacos，账号 nacos，口令为 .env 中的 TUOYU_NACOS_PASSWORD）'
        Write-Info '  查看进度：.\dev.ps1 compose core ps；Nacos 配置导入日志：.\dev.ps1 compose core logs nacos-init'
        Write-Info '  .env 中的地址保持 localhost / 127.0.0.1 即可在 IDE 中启动各服务；表结构由服务启动时的 Flyway 迁移创建'
        return
    }

    Write-Info '没有可用的 Docker（未安装或未启动），检查本机的中间件（地址与口令来自 .env）：'
    $ok = Test-Database
    $ok = (Test-Redis) -and $ok
    Test-Nacos | Out-Null
    Write-Info ''
    if ($ok) {
        Write-Info 'MySQL、Redis 可以连接。注意：'
    } else {
        Write-Info '部分中间件不可用，请先安装或启动，并核对 .env 中的地址与口令：'
        Write-Info '  - MySQL 8.x：TUOYU_DB_HOST、TUOYU_DB_PORT、TUOYU_DB_USERNAME、TUOYU_DB_PASSWORD'
        Write-Info '  - Redis：TUOYU_REDIS_HOST、TUOYU_REDIS_PORT、TUOYU_REDIS_PASSWORD（需要开启口令）'
    }
    Write-Info '  - 需要 tuoyu-cloud、tuoyu-workflow、tuoyu-job 三个库：首次使用执行 deploy/docker/mysql/init/01-create-databases.sql 建库，表由服务启动时的 Flyway 迁移创建'
    Write-Info '  - 单体（.\dev.ps1 monolith）不需要 Nacos；微服务方式需要 Nacos 2.x，配置导入：.\dev.ps1 nacos-import（预览）→ .\dev.ps1 nacos-import --apply'
    if (-not $ok) { exit 1 }
}

function Invoke-Down([string[]]$Arguments) {
    if (-not (Test-DockerReady)) { Stop-WithError '没有可用的 Docker' }
    $composeArgs = @('compose', '-p', 'tuoyu', 'down', '--remove-orphans') + $Arguments
    & docker @composeArgs
    exit $LASTEXITCODE
}

function Invoke-Status {
    Write-Info '环境：'
    if (Test-Path -LiteralPath '.env') { Write-Info '  .env      已存在' } else { Write-Info '  .env      不存在（.\dev.ps1 env 生成）' }
    $java = Get-JavaCommand
    if ($java) {
        $major = Get-JavaMajor $java
        if ($major -ge 21) { Write-Info "  JDK       $major（$java）" } else { Write-Info "  JDK       $major（$java），构建与运行需要 JDK 21" }
    } else {
        Write-Info '  JDK       未找到'
    }
    if (Get-Command mvn -ErrorAction SilentlyContinue) { Write-Info '  Maven     mvn' } else { Write-Info '  Maven     未在 PATH 中，将使用 mvnw.cmd' }
    $docker = Test-DockerReady
    if ($docker) { Write-Info '  Docker    可用' } else { Write-Info '  Docker    不可用' }
    if (Test-Path -LiteralPath $MonolithJar) { Write-Info "  单体 jar  $MonolithJar" }

    Write-Info ''
    Write-Info '中间件：'
    Test-Database | Out-Null
    Test-Redis | Out-Null
    Test-Nacos | Out-Null

    Write-Info ''
    Write-Info '服务端口（本机）：'
    $services = [ordered]@{
        '网关 / 单体' = 8081; 'tuoyu-system' = 9201; 'tuoyu-auth' = 9210; 'tuoyu-resource' = 9204; 'tuoyu-workflow' = 9205
        'tuoyu-gen' = 9202; 'tuoyu-job' = 9203; 'tuoyu-monitor' = 9100; 'tuoyu-snailjob-server' = 8800
    }
    foreach ($entry in $services.GetEnumerator()) {
        if (Test-TcpPort '127.0.0.1' $entry.Value) {
            Write-Info "  [运行中] $($entry.Key)  127.0.0.1:$($entry.Value)"
        } else {
            Write-Info "  [未运行] $($entry.Key)  127.0.0.1:$($entry.Value)"
        }
    }

    if ($docker) {
        Write-Info ''
        Write-Info 'compose 容器（项目 tuoyu）：'
        & docker compose -p tuoyu ps
    }
}

function Invoke-Monolith([string[]]$Arguments) {
    $build = $true
    $appArgs = @($Arguments)
    if ($appArgs.Length -gt 0 -and $appArgs[0] -eq '--no-build') {
        $build = $false
        $appArgs = @($appArgs | Select-Object -Skip 1)
    }
    if (-not (Test-Path -LiteralPath "$MonolithModule/pom.xml")) { Stop-WithError "找不到单体启动器模块 $MonolithModule" }
    if (-not (Test-Path -LiteralPath '.env')) {
        Write-Info '未找到 .env，先生成：'
        if (-not (Invoke-Env @())) { exit 1 }
        Write-Info ''
    }
    $java = Get-RequiredJava 21
    if ($build -or -not (Test-Path -LiteralPath $MonolithJar)) {
        $mvn = Get-MavenCommand
        Write-Info "构建单体（mvn -pl $MonolithModule -am package，会运行测试）..."
        & $mvn -B -ntp -pl $MonolithModule -am package
        if ($LASTEXITCODE -ne 0) { Stop-WithError '构建失败' }
    }
    Write-Info ''
    Write-Info '启动单体：http://localhost:8081（配置读取 deploy/config/nacos，地址与口令来自 .env；Ctrl+C 停止）'
    $javaArgs = @('-jar', $MonolithJar) + $appArgs
    & $java @javaArgs
    exit $LASTEXITCODE
}

function Invoke-NacosImport([string[]]$Arguments) {
    $java = Get-RequiredJava 21
    $toolArgs = @('tools/nacos/ImportNacosConfig.java') + $Arguments
    & $java @toolArgs
    exit $LASTEXITCODE
}

function Invoke-Compose([string[]]$Arguments) {
    $name = if ($Arguments.Length -gt 0) { $Arguments[0] } else { '' }
    $files = Get-ComposeFiles $name
    if (-not $files) { Stop-WithError '用法：.\dev.ps1 compose <core|full|monolith|extras> [docker compose 参数...]' }
    if (-not (Test-DockerReady)) { Stop-WithError '没有可用的 Docker' }
    if (-not (Test-Path -LiteralPath '.env')) { Stop-WithError '未找到 .env，请先执行 .\dev.ps1 env' }
    $composeArgs = @('compose', '--env-file', '.env') + $files + @($Arguments | Select-Object -Skip 1)
    & docker @composeArgs
    exit $LASTEXITCODE
}

$command = if ($args.Length -gt 0) { [string]$args[0] } else { 'help' }
$rest = @()
if ($args.Length -gt 1) { $rest = @($args[1..($args.Length - 1)] | ForEach-Object { [string]$_ }) }

Push-Location $Root
try {
    switch ($command) {
        'env' { if (-not (Invoke-Env $rest)) { exit 1 } }
        'up' { Invoke-Up }
        'down' { Invoke-Down $rest }
        'status' { Invoke-Status }
        'monolith' { Invoke-Monolith $rest }
        'nacos-import' { Invoke-NacosImport $rest }
        'compose' { Invoke-Compose $rest }
        { $_ -in @('help', '-h', '--help') } { Show-Usage }
        default { Show-Usage; exit 1 }
    }
} finally {
    Pop-Location
}
