# 生成业委会 App 测试用会议录音（离线 TTS）
#   录音脚本\*.txt  ->  会议录音\*.mp3（16k 单声道，直接可上传给 ASR 识别）
# 用法：powershell -ExecutionPolicy Bypass -File gen_audio.ps1
Add-Type -AssemblyName System.Speech

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$srcDir = Join-Path $root '录音脚本'
$outDir = Join-Path $root '会议录音'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }

$syn = New-Object System.Speech.Synthesis.SpeechSynthesizer
try { $syn.SelectVoice('Microsoft Huihui Desktop') } catch { Write-Host '未找到中文语音 Huihui，改用默认语音' }
$syn.Rate = -1   # 略慢，吐字更清楚，利于识别

Get-ChildItem -Path $srcDir -Filter '*.txt' | Sort-Object Name | ForEach-Object {
  $name = [System.IO.Path]::GetFileNameWithoutExtension($_.Name)
  $text = Get-Content -Raw -Encoding UTF8 $_.FullName
  $wav = Join-Path $outDir ($name + '.wav')
  $mp3 = Join-Path $outDir ($name + '.mp3')

  $syn.SetOutputToWaveFile($wav)
  $syn.Speak($text)
  $syn.SetOutputToNull()

  # 转 16k 单声道 mp3（与后端 ASR 目标格式一致，体积更小）
  & ffmpeg -y -loglevel error -i $wav -ac 1 -ar 16000 -b:a 64k $mp3
  Remove-Item $wav -ErrorAction SilentlyContinue
  Write-Host ('录音 -> 会议录音\' + $name + '.mp3')
}
$syn.Dispose()
Write-Host '全部完成。'
