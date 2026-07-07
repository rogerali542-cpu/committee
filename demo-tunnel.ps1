# demo-tunnel.ps1 — one-shot bring-up of the full local stack + a public URL for phone testing.
#   OCR-ASR :8003  +  Spring Boot :8080  +  h5 Vite :5173  ->  cloudflared quick tunnel
#   -> prints  https://<random>.trycloudflare.com  (also copies it to the clipboard)
#
#   Usage:  powershell -ExecutionPolicy Bypass -File demo-tunnel.ps1
#
#   NOTE: the quick-tunnel URL is RANDOM and CHANGES every time cloudflared (re)starts.
#         Keep this window's processes alive to keep the same URL; re-run to get a new one.
#   The frontend must be the DEV server (npm run dev), not preview — the /api + /asr-ws
#   proxies only exist on the dev server (see h5/vite.config.js).

$root = $PSScriptRoot
$py   = 'C:\Users\Lequan\AppData\Local\Python\pythoncore-3.14-64\python.exe'
$cf   = 'C:\Users\Lequan\Downloads\cloudflared.exe'
$logs = Join-Path $env:TEMP 'ywh-demo'
New-Item -ItemType Directory -Force -Path $logs | Out-Null

function Up($p){ try{ $c=New-Object Net.Sockets.TcpClient; $c.Connect('localhost',$p); $c.Close(); $true }catch{ $false } }

# 1) OCR-ASR :8003 + backend :8080 — reuse the existing helper (idempotent: skips ports already up).
#    If the backend fails to come up, its window used mvn -o (offline); run `mvn spring-boot:run`
#    (no -o) in .\backend once to refresh .m2, then re-run this script.
& (Join-Path $root 'dev-start.ps1')

# 2) Frontend Vite dev server :5173
if (Up 5173) {
  Write-Host '[skip] Vite already on :5173'
} else {
  Start-Process -FilePath 'npm.cmd' -ArgumentList 'run','dev' -WorkingDirectory (Join-Path $root 'h5') `
    -RedirectStandardOutput "$logs\h5.out.log" -RedirectStandardError "$logs\h5.err.log" -WindowStyle Hidden
  Write-Host '[ok] Vite dev starting on :5173'
}

# wait for all three local ports
Write-Host -NoNewline 'waiting for OCR / backend / Vite '
for ($i=0; $i -lt 30; $i++) { if ((Up 8003) -and (Up 8080) -and (Up 5173)) { break }; Write-Host -NoNewline '.'; Start-Sleep 2 }
Write-Host ''
Write-Host "  OCR :8003 = $(if(Up 8003){'UP'}else{'DOWN'})"
Write-Host "  API :8080 = $(if(Up 8080){'UP'}else{'DOWN'})"
Write-Host "  Web :5173 = $(if(Up 5173){'UP'}else{'DOWN'})"

# 3) cloudflared quick tunnel -> :5173
if (Get-Process cloudflared -ErrorAction SilentlyContinue) {
  Write-Host '[warn] cloudflared already running. For a fresh URL: Stop-Process -Name cloudflared, then re-run.'
  Write-Host "       (last tunnel log: $logs\cf.err.log)"
} else {
  Remove-Item "$logs\cf.err.log","$logs\cf.out.log" -ErrorAction SilentlyContinue
  Start-Process -FilePath $cf -ArgumentList 'tunnel','--url','http://localhost:5173' `
    -RedirectStandardOutput "$logs\cf.out.log" -RedirectStandardError "$logs\cf.err.log" -WindowStyle Hidden
  $url = $null
  for ($i=0; $i -lt 30; $i++) {
    Start-Sleep 2
    $t = Get-Content "$logs\cf.err.log","$logs\cf.out.log" -Raw -ErrorAction SilentlyContinue
    if ($t -match 'https://[a-z0-9-]+\.trycloudflare\.com') { $url = $Matches[0]; break }
  }
  Write-Host ''
  if ($url) {
    Write-Host '==================================================================='
    Write-Host "   PHONE URL :  $url"
    Write-Host '==================================================================='
    try { Set-Clipboard $url; Write-Host '   (copied to clipboard)' } catch {}
  } else {
    Write-Host "[err] tunnel URL not found yet — check $logs\cf.err.log"
  }
}
