# Sync local dev DB (ywh_db) to cloud RDS (committee) as a FULL SNAPSHOT overwrite.
# Run this when connected to the whitelisted WiFi. Requires Docker Desktop running.
# Credentials are read from backend/.env (local) and deploy/docker/.env (cloud) - never hardcoded here.
# NOTE: cloud data written by the sandbox demo since last sync will be REPLACED by local data.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

function Read-EnvValue([string]$file, [string]$key) {
  $line = Get-Content $file -Encoding UTF8 | ForEach-Object { $_.TrimStart([char]0xFEFF).Trim() } | Where-Object { $_ -match "^$key=" } | Select-Object -First 1
  if (-not $line) { throw "Missing $key in $file" }
  return ($line -replace "^$key=", '').Trim()
}

$localPw  = Read-EnvValue (Join-Path $root 'backend\.env') 'DB_PASSWORD'
$cloudEnv = Join-Path $root 'deploy\docker\.env'
$ch = Read-EnvValue $cloudEnv 'DB_HOST'
$cu = Read-EnvValue $cloudEnv 'DB_USER'
$cp = Read-EnvValue $cloudEnv 'DB_PASSWORD'
$cn = Read-EnvValue $cloudEnv 'DB_NAME'

Write-Host "[1/4] Checking cloud RDS reachability ($ch:3306)..."
$tcp = New-Object Net.Sockets.TcpClient
try {
  $ok = $tcp.BeginConnect($ch, 3306, $null, $null).AsyncWaitHandle.WaitOne(8000)
  if (-not $ok -or -not $tcp.Connected) { throw 'timeout' }
} catch {
  Write-Host '[FAIL] Cannot reach cloud RDS 3306.'
  Write-Host '       Are you on the whitelisted WiFi? Is the VPN disconnected?'
  exit 1
} finally { $tcp.Close() }
Write-Host '      OK'

$backupDir = Join-Path $root 'deploy\database-export'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$cloudBackup = Join-Path $backupDir "cloud_before_sync_$stamp.sql"
Write-Host "[2/5] Backing up current cloud database before overwrite..."
$env:MYSQL_PWD = $cp
cmd /c "docker run --rm -e MYSQL_PWD ywh-mysql:latest mysqldump -h $ch -u $cu --single-transaction --set-gtid-purged=OFF --routines --triggers $cn > `"$cloudBackup`""
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $cloudBackup) -or (Get-Item $cloudBackup).Length -eq 0) {
  Write-Host '[FAIL] cloud backup failed; cloud database was NOT modified.'
  exit 1
}
Write-Host "      OK -> $cloudBackup"

# Use cmd.exe raw byte redirection for dump/import: PowerShell 5.1 pipes re-encode
# native output (GBK console + BOM) and would corrupt Chinese data in the dump.
$dump = Join-Path $root 'deploy\docker\db-seed\ywh_db_full.sql'
Write-Host '[3/5] Dumping local ywh_db (full snapshot)...'
$env:MYSQL_PWD = $localPw
cmd /c "docker run --rm -e MYSQL_PWD ywh-mysql:latest mysqldump -h host.docker.internal -u root --single-transaction --set-gtid-purged=OFF ywh_db > `"$dump`""
if ($LASTEXITCODE -ne 0) { Write-Host '[FAIL] dump failed (is local MySQL and Docker running?)'; exit 1 }
Write-Host "      OK -> $dump"

Write-Host "[4/5] Importing into cloud $cn (overwrites existing tables)..."
$env:MYSQL_PWD = $cp
cmd /c "docker run --rm -i -e MYSQL_PWD ywh-mysql:latest mysql -h $ch -u $cu --default-character-set=utf8mb4 $cn < `"$dump`""
if ($LASTEXITCODE -ne 0) { Write-Host '[FAIL] import failed'; exit 1 }
Write-Host '      OK'

Write-Host '[5/5] Verifying...'
docker run --rm -e MYSQL_PWD ywh-mysql:latest mysql -h $ch -u $cu -e "SELECT (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$cn') AS tables_n, (SELECT COUNT(*) FROM $cn.users) AS users_n, (SELECT COUNT(*) FROM $cn.user_roles) AS roles_n;"
$env:MYSQL_PWD = ''
Write-Host 'DONE. Cloud DB now matches local. No backend restart needed.'
