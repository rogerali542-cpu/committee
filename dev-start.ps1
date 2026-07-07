# dev-start.ps1 — one-shot bring-up of the two backend services (business-committee H5 app)
#   OCR-ASR (FastAPI) :8003  +  Spring Boot backend :8080
#   Frontend h5(:5173) is started separately (preview tooling or: npm run dev --prefix h5).
#   Ports already in use are skipped (no double-start).
#   Usage:  powershell -ExecutionPolicy Bypass -File dev-start.ps1

$root = $PSScriptRoot
$py   = 'C:\Users\Lequan\AppData\Local\Python\pythoncore-3.14-64\python.exe'
$mvn  = 'C:\Users\Lequan\.m2\wrapper\dists\apache-maven-3.9.6-bin\3311e1d4\apache-maven-3.9.6\bin\mvn.cmd'
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'

function Test-Port($p) {
  try { $c = New-Object Net.Sockets.TcpClient; $c.Connect('localhost', $p); $c.Close(); $true }
  catch { $false }
}

# 1) OCR-ASR :8003  (must match backend OCR_SERVICE_BASE_URL).
#    NOTE: an unrelated OCR deployment sometimes runs on :8007 (different model) - that is NOT this project's; use 8003.
if (Test-Port 8003) {
  Write-Host '[skip] OCR-ASR already on :8003'
} else {
  $ocrArgs = @('-m', 'uvicorn', 'ocrasr.main:app', '--host', '0.0.0.0', '--port', '8003')
  Start-Process -FilePath $py -ArgumentList $ocrArgs -WorkingDirectory (Join-Path $root 'ocr-asr-service') -WindowStyle Minimized
  Write-Host '[ok] OCR-ASR started on :8003'
}

# 2) Spring Boot :8080  (inherits JAVA_HOME set above).
if (Test-Port 8080) {
  Write-Host '[skip] backend already on :8080'
} else {
  $mvnArgs = @('-o', 'spring-boot:run')
  Start-Process -FilePath $mvn -ArgumentList $mvnArgs -WorkingDirectory (Join-Path $root 'backend') -WindowStyle Minimized
  Write-Host '[ok] backend started on :8080 (ready in ~15s)'
}

Write-Host ''
Write-Host 'Self-check:  curl http://localhost:8003/   and   http://localhost:8080/actuator/health (403 is normal)'
