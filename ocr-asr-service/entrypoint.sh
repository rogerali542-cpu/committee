#!/bin/bash
set -e

# 启动前依赖自检: 缺关键包时立刻明确报错退出, 避免被 uvicorn 长 traceback 淹没.
echo "Checking critical Python dependencies..."
python - <<'PYEOF'
import importlib, sys

required = [
    ("multipart", "python-multipart"),
    ("fastapi", "fastapi"),
    ("uvicorn", "uvicorn"),
    ("aiohttp", "aiohttp"),
    ("openai", "openai"),
]
missing = []
for mod, pkg in required:
    try:
        importlib.import_module(mod)
    except ImportError:
        missing.append(pkg)

if missing:
    sys.stderr.write(
        "\n[启动自检失败] 当前镜像缺少依赖: %s\n"
        "请将其加入 requirements-core.txt 后重建镜像 (docker build --no-cache ...).\n\n"
        % ", ".join(missing)
    )
    sys.exit(1)

print("Dependency check passed.")
PYEOF

# ffmpeg 自检: ASR 转码必需 (OCR 不依赖). 缺失只告警, 不阻断 (OCR 仍可用).
if ! command -v ffmpeg >/dev/null 2>&1; then
    echo "[告警] 未检测到 ffmpeg, 语音识别 (ASR) 将不可用; OCR 不受影响."
fi

echo "Starting OCR & ASR Service..."
exec "$@"
