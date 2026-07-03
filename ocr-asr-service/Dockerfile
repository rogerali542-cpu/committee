# OCR & ASR Service 生产镜像
# 构建 (在本目录): docker build -t ocr-asr-service:latest .
ARG BASE_IMAGE=python:3.11-slim
FROM ${BASE_IMAGE}

WORKDIR /app

# ffmpeg: ASR 把浏览器 webm/opus 转 16k mono PCM 必需; curl: 健康检查用.
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements-core.txt /app/requirements-core.txt
# 使用清华 TUNA 镜像加速; 网络可直连 PyPI 时可删掉 -i / --trusted-host 两行.
RUN pip install --no-cache-dir \
    -i https://pypi.tuna.tsinghua.edu.cn/simple/ \
    --trusted-host pypi.tuna.tsinghua.edu.cn \
    -r /app/requirements-core.txt

COPY ocrasr/ /app/ocrasr/

COPY entrypoint.sh /entrypoint.sh
RUN sed -i 's/\r$//' /entrypoint.sh 2>/dev/null || true \
    && chmod +x /entrypoint.sh

ENV PYTHONUNBUFFERED=1
ENV PYTHONPATH=/app
EXPOSE 8003

ENTRYPOINT ["/entrypoint.sh"]
CMD ["uvicorn", "ocrasr.main:app", "--host", "0.0.0.0", "--port", "8003"]
