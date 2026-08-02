<#
.SYNOPSIS
  Stop the ERP local dev stack: kill processes on 8080 (backend) / 5173 (frontend), and stop Redis.

.PARAMETER KeepRedis
  Leave Redis running.
#>
param(
  [switch]$KeepRedis
)

function Stop-Port([int]$port, [string]$label) {
  $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
  if ($conns) {
    $conns.OwningProcess | Select-Object -Unique | ForEach-Object {
      try {
        Stop-Process -Id $_ -Force -ErrorAction Stop
        Write-Host "Stopped $label (port $port, PID $_)" -ForegroundColor Green
      } catch {
        Write-Host "Failed to stop PID $_ : $($_.Exception.Message)" -ForegroundColor Yellow
      }
    }
  } else {
    Write-Host "$label not running (port $port)" -ForegroundColor DarkGray
  }
}

# Backend 8080 (also ends its mvn/java process)
Stop-Port 8080 'backend'
# Frontend 5173 (vite/node)
Stop-Port 5173 'frontend'

# Redis
if (-not $KeepRedis) {
  $redis = Get-Process redis-server -ErrorAction SilentlyContinue
  if ($redis) {
    $redis | Stop-Process -Force -ErrorAction SilentlyContinue
    Write-Host "Stopped Redis" -ForegroundColor Green
  } else {
    Write-Host "Redis not running" -ForegroundColor DarkGray
  }
}

Write-Host "Done. Note: the backend/frontend PowerShell windows may stay open (idle) - close them manually." -ForegroundColor Cyan
