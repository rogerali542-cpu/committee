"""
OCR & ASR Service 调用示例.
跑之前: pip install httpx, 并把 BASE_URL / TOKEN 改成你的部署值.

  python examples/client_example.py
"""
import asyncio
import json

import httpx

BASE_URL = "http://127.0.0.1:8003"
TOKEN = "在这里填你的 INTERNAL_TOKEN"
HEADERS = {"X-Internal-Token": TOKEN}


async def ocr_invoice(image_path: str) -> dict:
    """发票结构化识别: 图片 -> 票面要素 (金额/税额/购销方等)."""
    with open(image_path, "rb") as f:
        files = {"file": (image_path, f.read(), "image/jpeg")}
    async with httpx.AsyncClient(timeout=120) as client:
        resp = await client.post(f"{BASE_URL}/v1/ocr/invoice", headers=HEADERS, files=files)
        resp.raise_for_status()
        return resp.json()


async def ocr_image(image_path: str) -> dict:
    """整页 OCR: 图片 -> 全文文字."""
    with open(image_path, "rb") as f:
        files = {"file": (image_path, f.read(), "image/png")}
    async with httpx.AsyncClient(timeout=120) as client:
        resp = await client.post(f"{BASE_URL}/v1/ocr/image", headers=HEADERS, files=files)
        resp.raise_for_status()
        return resp.json()


async def asr_recognize(audio_path: str) -> dict:
    """语音识别 (同步版): 音频文件 (webm/wav/mp3...) -> 文字."""
    with open(audio_path, "rb") as f:
        files = {"file": (audio_path, f.read(), "audio/webm")}
    async with httpx.AsyncClient(timeout=180) as client:
        resp = await client.post(f"{BASE_URL}/v1/asr/recognize", headers=HEADERS, files=files)
        resp.raise_for_status()
        return resp.json()


async def meeting_minutes(segments: list[dict]) -> dict:
    """会议纪要: 转写片段 -> 结构化纪要 (摘要/结论/风险/待确认)."""
    payload = {"segments": segments}
    async with httpx.AsyncClient(timeout=180) as client:
        resp = await client.post(
            f"{BASE_URL}/v1/meetings/generate-minutes",
            headers={**HEADERS, "Content-Type": "application/json"},
            json=payload,
        )
        resp.raise_for_status()
        return resp.json()


async def main():
    # 1) 发票识别
    # print(json.dumps(await ocr_invoice("sample_invoice.jpg"), ensure_ascii=False, indent=2))

    # 2) 整页 OCR
    # print(json.dumps(await ocr_image("sample_page.png"), ensure_ascii=False, indent=2))

    # 3) 语音识别
    # print(json.dumps(await asr_recognize("sample.webm"), ensure_ascii=False, indent=2))

    # 4) 会议纪要 (拿 3) 的 utterances 或自己造片段)
    demo_segments = [
        {"id": 1, "speaker_label": "speaker_1", "text": "这一期我们先把发票识别上线, 下周联调."},
        {"id": 2, "speaker_label": "speaker_2", "text": "风险是火山的 key 还没批下来, 我去催一下."},
    ]
    print(json.dumps(await meeting_minutes(demo_segments), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    asyncio.run(main())
