@echo off
rem 手机预览通道（0731）：把本机 Vite dev 服务器通过 Cloudflare 免费隧道暴露成公网 https 地址。
rem 用法：先在另一个窗口跑起前端（cd h5 && npm run dev，记下端口），再双击本脚本；
rem      若 dev 端口不是 5173（比如被占用顺延成 5174），带端口跑：开手机预览.bat 5174
rem 出现 https://xxxx.trycloudflare.com 后用手机浏览器打开即可（每次启动网址会变）。
chcp 65001 >nul

set PORT=%1
if "%PORT%"=="" set PORT=5173

where cloudflared >nul 2>nul
if errorlevel 1 (
  echo [1/2] cloudflared not found, installing via winget...
  winget install --id Cloudflare.cloudflared -e --accept-source-agreements --accept-package-agreements
  if errorlevel 1 (
    echo.
    echo install failed. Manual: download cloudflared-windows-amd64.exe from
    echo   https://github.com/cloudflare/cloudflared/releases
    echo rename to cloudflared.exe, put it next to this .bat, then run again.
    pause
    exit /b 1
  )
)

echo [2/2] tunnel to http://localhost:%PORT%  (make sure "npm run dev" is running there)
echo waiting for https://xxxx.trycloudflare.com ... open that URL on your phone.
echo.
cloudflared tunnel --url http://localhost:%PORT%
pause
