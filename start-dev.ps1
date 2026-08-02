<#
.SYNOPSIS
  One-click start for the XeFlow local development stack.

.DESCRIPTION
  - Backend and frontend each run in their own PowerShell window so you can read logs / Ctrl+C individually.
  - Redis starts as a hidden background process; if redis-server.exe is not found it falls back to the
    in-memory session store (no Redis required).
  - Prerequisite: local MySQL is running and sql/ has been initialized (database 'erp').

.PARAMETER Store
  Session store: redis (default) or memory.

.PARAMETER RedisHome
  Portable Redis directory containing redis-server.exe. Auto-detected by default.

.EXAMPLE
  ./start-dev.ps1                 # Redis + backend + frontend
  ./start-dev.ps1 -Store memory   # no Redis, in-memory session
#>
param(
  [ValidateSet('redis', 'memory')]
  [string]$Store = 'redis',
  [string]$RedisHome = '',
  [string]$MysqlUser = 'root',
  [string]$MysqlPassword = ''
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
Set-Location $root

function Write-Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Warn2($msg) { Write-Host "[!] $msg" -ForegroundColor Yellow }
function Test-Port([int]$port) {
  return [bool](Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
}
function Import-DotEnv([string]$path) {
  if (-not (Test-Path $path)) { return }
  Write-Step "Loading local environment: $path"
  Get-Content $path | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith('#') -or -not $line.Contains('=')) { return }
    $idx = $line.IndexOf('=')
    $key = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim().Trim('"').Trim("'")
    if ($key) { [Environment]::SetEnvironmentVariable($key, $value, 'Process') }
  }
}

Import-DotEnv (Join-Path $root '.env')

if (-not $PSBoundParameters.ContainsKey('MysqlUser') -and $env:MYSQL_USER) {
  $MysqlUser = $env:MYSQL_USER
}
if (-not $PSBoundParameters.ContainsKey('MysqlPassword') -and $env:MYSQL_PASSWORD) {
  $MysqlPassword = $env:MYSQL_PASSWORD
}
if (-not $MysqlPassword) {
  throw 'MySQL password is required. Set MYSQL_PASSWORD in .env or pass -MysqlPassword.'
}

# ---- 0. tool check ----
Write-Step "Checking toolchain"
foreach ($tool in 'java', 'mvn', 'node', 'npm') {
  if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
    throw "Missing '$tool' on PATH. Please install it first."
  }
}
Write-Host "    java/mvn/node/npm OK"

# ---- 1. MySQL reachability hint ----
if (Test-Port 3306) {
  Write-Host "    MySQL 3306 is up"
} else {
  Write-Warn2 "MySQL (3306) not detected. Ensure it is running and sql/01_schema.sql + 02_seed.sql were applied."
}

# ---- 2. Redis ----
if ($Store -eq 'redis') {
  if (Test-Port 6379) {
    Write-Step "Redis already running (6379)"
  } else {
    $candidates = @()
    if ($RedisHome) { $candidates += $RedisHome }
    if ($env:REDIS_HOME) { $candidates += $env:REDIS_HOME }
    $candidates += (Join-Path $HOME 'redis-win')
    $candidates += (Join-Path $root '.tools\redis')

    $redisExe = $null
    foreach ($c in $candidates) {
      $exe = Join-Path $c 'redis-server.exe'
      if (Test-Path $exe) { $redisExe = $exe; break }
    }

    if ($redisExe) {
      Write-Step "Starting Redis: $redisExe"
      Start-Process -FilePath $redisExe -ArgumentList '--port 6379 --save "" --appendonly no' -WorkingDirectory (Split-Path $redisExe) -WindowStyle Hidden | Out-Null
      Start-Sleep -Seconds 1
      if (Test-Port 6379) { Write-Host "    Redis started" }
      else { Write-Warn2 "Redis port not ready, falling back to in-memory session"; $Store = 'memory' }
    } else {
      Write-Warn2 "redis-server.exe not found (looked in: $($candidates -join '; '))"
      Write-Warn2 "Falling back to in-memory session store. Use -RedisHome <dir> to point at Redis."
      $Store = 'memory'
    }
  }
}
Write-Step "Session store: $Store"

# ---- 3. backend (new window) ----
Write-Step "Starting backend (new window, http://localhost:8080/api)"
$backendCmd = "`$Host.UI.RawUI.WindowTitle='XeFlow Backend'; `$env:ERP_SESSION_STORE='$Store'; `$env:MYSQL_USER='$MysqlUser'; `$env:MYSQL_PASSWORD='$MysqlPassword'; mvn -DskipTests spring-boot:run"
Start-Process powershell -ArgumentList '-NoExit', '-Command', $backendCmd -WorkingDirectory (Join-Path $root 'erp-backend')

# ---- 4. frontend (new window) ----
Write-Step "Starting frontend (new window, http://localhost:5173)"
$frontendCmd = "`$Host.UI.RawUI.WindowTitle='XeFlow Frontend'; if (-not (Test-Path 'node_modules')) { npm install }; npm run dev"
Start-Process powershell -ArgumentList '-NoExit', '-Command', $frontendCmd -WorkingDirectory (Join-Path $root 'erp-frontend')

# ---- done ----
Write-Host ""
Write-Host "==================================================" -ForegroundColor Green
Write-Host " Dev environment starting..." -ForegroundColor Green
Write-Host "   Frontend:  http://localhost:5173" -ForegroundColor Green
Write-Host "   Backend:   http://localhost:8080/api" -ForegroundColor Green
Write-Host "   API docs:  http://localhost:8080/api/doc.html" -ForegroundColor Green
Write-Host "   Login:     admin / the password configured in ERP_BOOTSTRAP_ADMIN_PASSWORD" -ForegroundColor Green
Write-Host "--------------------------------------------------" -ForegroundColor Green
Write-Host " Backend compiles on first run; wait ~10-30s before opening the frontend." -ForegroundColor Green
Write-Host " Stop everything: ./stop-dev.ps1" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Green
