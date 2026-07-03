"""
火山引擎大模型流式语音识别 (sauc bigmodel) 客户端.

简化策略 (相比官方 demo):
- 移除"实时流式"模拟 (不 sleep, 一次性把所有 chunk 发完, 服务端攒齐后返回 is_last_package=True)
- 服务器实际仍按 segment 边收边识别, 但客户端不关心中间结果, 只取最终 text
- 输入: 浏览器上传的 webm/opus blob → ffmpeg subprocess 转 16kHz/16bit/mono WAV → 提取 raw PCM
- 输出: {text, utterances, duration_ms, request_id, log_id}

协议参考: services/ai_reviewer/_local_demo/sauc_websocket_demo.py (官方 Python demo)
对应 .env 配置: VOLC_ASR_* 18 字段
"""
from __future__ import annotations

import asyncio
import glob
import gzip
import json
import os
import shutil
import struct
import subprocess
import uuid
from functools import lru_cache
from typing import Any, AsyncGenerator, Dict, List, Optional, Tuple

import aiohttp
from loguru import logger  # ai_reviewer 项目统一用 loguru (标准 logging 默认 WARN+, INFO 看不到)

from ocrasr.config import settings


# ============ ffmpeg 路径探测 ============

@lru_cache(maxsize=1)
def _find_ffmpeg() -> str:
    """
    返回 ffmpeg.exe 可执行路径.
    优先级: FFMPEG_BIN env > shutil.which (PATH) > 常见 Windows winget 包路径 > Linux 标准路径.
    避免依赖 shell PATH 刷新 (winget 装完老 shell PATH 不更新的常见坑).
    """
    explicit = os.getenv("FFMPEG_BIN", "").strip()
    if explicit and os.path.isfile(explicit):
        return explicit

    found = shutil.which("ffmpeg")
    if found:
        return found

    # Windows winget 默认安装路径
    user_home = os.path.expanduser("~")
    patterns = [
        os.path.join(user_home, "AppData", "Local", "Microsoft", "WinGet", "Packages",
                     "Gyan.FFmpeg*", "ffmpeg-*", "bin", "ffmpeg.exe"),
        os.path.join(user_home, "AppData", "Local", "Microsoft", "WinGet", "Packages",
                     "Gyan.FFmpeg.Essentials*", "ffmpeg-*", "bin", "ffmpeg.exe"),
        r"C:\Program Files\ffmpeg\bin\ffmpeg.exe",
        r"D:\ffmpeg\bin\ffmpeg.exe",
        "/usr/bin/ffmpeg",
        "/usr/local/bin/ffmpeg",
    ]
    for pattern in patterns:
        matches = glob.glob(pattern)
        if matches:
            logger.info(f"[asr] ffmpeg via fallback: {matches[0]}")
            return matches[0]

    raise FileNotFoundError(
        "ffmpeg 未找到. 装一下 (Windows: winget install --id=Gyan.FFmpeg -e), "
        "或设环境变量 FFMPEG_BIN 指向 ffmpeg.exe 绝对路径"
    )


# ============ 协议常量 ============

class _ProtocolVersion:
    V1 = 0b0001


class _MessageType:
    CLIENT_FULL_REQUEST = 0b0001
    CLIENT_AUDIO_ONLY_REQUEST = 0b0010
    SERVER_FULL_RESPONSE = 0b1001
    SERVER_ERROR_RESPONSE = 0b1111


class _MessageTypeSpecificFlags:
    POS_SEQUENCE = 0b0001
    NEG_WITH_SEQUENCE = 0b0011  # 最后一包 (负序号)


class _SerializationType:
    JSON = 0b0001


class _CompressionType:
    GZIP = 0b0001


# ============ 错误 ============

class AsrError(Exception):
    """ASR 调用失败"""
    def __init__(self, code: int, message: str, log_id: str = ""):
        self.code = code
        self.message = message
        self.log_id = log_id
        super().__init__(f"ASR error {code}: {message} (log_id={log_id})")


class AsrConfigError(Exception):
    """ASR 配置缺失 (VOLC_ASR_* 未配)"""


# ============ Header 构造 ============

def _build_header(
    message_type: int,
    flags: int,
    serialization: int = _SerializationType.JSON,
    compression: int = _CompressionType.GZIP,
) -> bytes:
    """4 字节协议头"""
    return bytes([
        (_ProtocolVersion.V1 << 4) | 0b0001,  # protocol_version | header_size=4 字节
        (message_type << 4) | flags,
        (serialization << 4) | compression,
        0x00,
    ])


def _build_full_client_request(seq: int) -> bytes:
    """首帧: 含 JSON 配置, seq=正"""
    header = _build_header(_MessageType.CLIENT_FULL_REQUEST, _MessageTypeSpecificFlags.POS_SEQUENCE)
    # 注意 format/codec 必须和实际送的音频字节对齐:
    # ffmpeg 用 "-f s16le" 输出无头 raw PCM, 所以 format=pcm + codec=raw.
    # 错配 (如 format=wav + codec=raw + 实际无 WAV header) 火山会报 45000151 "invalid WAV file format".
    payload_dict = {
        "user": {"uid": "nexus_ai_draft"},
        "audio": {
            "format": settings.VOLC_ASR_FORMAT,  # pcm
            "codec": settings.VOLC_ASR_CODEC,    # raw
            "rate": settings.VOLC_ASR_SAMPLE_RATE,
            "bits": settings.VOLC_ASR_BITS,
            "channel": settings.VOLC_ASR_CHANNEL,
        },
        "request": {
            "model_name": settings.VOLC_ASR_MODEL_NAME,
            "enable_punc": settings.VOLC_ASR_ENABLE_PUNC,
            "enable_itn": True,
            "show_utterances": True,
        },
    }
    payload_json = json.dumps(payload_dict, ensure_ascii=False).encode("utf-8")
    payload_compressed = gzip.compress(payload_json)
    return (
        header
        + struct.pack(">i", seq)
        + struct.pack(">I", len(payload_compressed))
        + payload_compressed
    )


def _build_audio_only_request(seq: int, audio_chunk: bytes, is_last: bool) -> bytes:
    """音频帧. is_last=True 时 seq 取负, flags=NEG_WITH_SEQUENCE"""
    flags = _MessageTypeSpecificFlags.NEG_WITH_SEQUENCE if is_last else _MessageTypeSpecificFlags.POS_SEQUENCE
    actual_seq = -seq if is_last else seq
    header = _build_header(
        _MessageType.CLIENT_AUDIO_ONLY_REQUEST,
        flags,
        serialization=0b0000,  # 音频不用 JSON 序列化
        compression=_CompressionType.GZIP,
    )
    chunk_compressed = gzip.compress(audio_chunk)
    return (
        header
        + struct.pack(">i", actual_seq)
        + struct.pack(">I", len(chunk_compressed))
        + chunk_compressed
    )


# ============ Response 解析 ============

def _parse_response(msg: bytes) -> Dict[str, Any]:
    """解析服务端二进制响应. 返回 {code, is_last, payload_seq, payload_msg(dict)}"""
    header_size = msg[0] & 0x0F
    message_type = msg[1] >> 4
    flags = msg[1] & 0x0F
    serialization = msg[2] >> 4
    compression = msg[2] & 0x0F

    payload = msg[header_size * 4:]

    result: Dict[str, Any] = {
        "code": 0,
        "is_last": False,
        "payload_seq": 0,
        "payload_msg": None,
    }

    if flags & 0x01:  # 含 sequence
        result["payload_seq"] = struct.unpack(">i", payload[:4])[0]
        payload = payload[4:]
    if flags & 0x02:  # 最后一包标志
        result["is_last"] = True
    if flags & 0x04:  # 含 event
        payload = payload[4:]

    if message_type == _MessageType.SERVER_FULL_RESPONSE:
        payload_size = struct.unpack(">I", payload[:4])[0]
        payload = payload[4:4 + payload_size]
    elif message_type == _MessageType.SERVER_ERROR_RESPONSE:
        result["code"] = struct.unpack(">i", payload[:4])[0]
        payload_size = struct.unpack(">I", payload[4:8])[0]
        payload = payload[8:8 + payload_size]
    else:
        return result

    if not payload:
        return result

    if compression == _CompressionType.GZIP:
        try:
            payload = gzip.decompress(payload)
        except Exception as e:
            logger.warning(f"[asr] payload gzip 解压失败: {e}")
            return result

    if serialization == _SerializationType.JSON:
        try:
            result["payload_msg"] = json.loads(payload.decode("utf-8"))
        except Exception as e:
            logger.warning(f"[asr] payload JSON 解析失败: {e}")

    return result


# ============ 音频转码 (任意格式 → 16kHz mono PCM raw) ============

def _convert_to_pcm_raw(audio_bytes: bytes, mime: str = "") -> bytes:
    """
    用 ffmpeg subprocess 把任意音频 (webm/opus/wav/mp3...) 转 16kHz/16bit/mono raw PCM (无 WAV 头).
    raw PCM 才是火山 ASR 配置 (format=pcm, codec=raw) 期望的输入.
    """
    sample_rate = settings.VOLC_ASR_SAMPLE_RATE
    try:
        ffmpeg_bin = _find_ffmpeg()
    except FileNotFoundError as e:
        raise AsrError(-1, str(e))
    # -v info: 输出输入 metadata (Input #0, codec, sample_rate, channels), 便于诊断格式问题
    cmd = [
        ffmpeg_bin,
        "-v", "info",
        "-y",
        "-i", "pipe:0",
        "-acodec", "pcm_s16le",
        "-ac", str(settings.VOLC_ASR_CHANNEL),
        "-ar", str(sample_rate),
        "-f", "s16le",   # raw PCM 输出 (无 WAV 头)
        "pipe:1",
    ]
    try:
        result = subprocess.run(
            cmd,
            input=audio_bytes,
            capture_output=True,
            check=True,
            timeout=30,
        )
    except FileNotFoundError:
        raise AsrError(-1, "ffmpeg 路径无效, 请检查 FFMPEG_BIN 或 winget 安装")
    except subprocess.CalledProcessError as e:
        stderr = (e.stderr or b"").decode("utf-8", errors="ignore")[:300]
        raise AsrError(-1, f"ffmpeg 转码失败: {stderr}")
    except subprocess.TimeoutExpired:
        raise AsrError(-1, "ffmpeg 转码超时 (30s)")

    pcm_bytes = result.stdout
    # 打 ffmpeg stderr (输入 metadata + 转码统计)
    stderr_text = (result.stderr or b"").decode("utf-8", errors="ignore")
    # 只取 Input #0 ... Output #0 ... size= 这几行关键信息
    key_lines = [ln for ln in stderr_text.splitlines() if any(k in ln for k in ("Input #", "Stream #", "Duration:", "Output #", "size=", "Audio:"))]
    if key_lines:
        logger.info("[asr] ffmpeg 输入分析:\n  " + "\n  ".join(key_lines[:10]))

    # PCM 静音检测: 统计绝对值 > 阈值的样本比例 (检测是否全静音 / 极小音量)
    # 16-bit PCM 样本范围 [-32768, 32767], 取绝对值 > 500 (约 -36 dBFS) 视为有声
    import struct as _struct
    sample_count = len(pcm_bytes) // 2
    if sample_count > 0:
        # 抽样检测前 8000 样本 (= 0.5 秒 @ 16kHz) 节省时间
        check_count = min(sample_count, 8000)
        non_silent = 0
        max_amp = 0
        sum_sq = 0
        for i in range(check_count):
            sample = _struct.unpack_from("<h", pcm_bytes, i * 2)[0]
            abs_v = abs(sample)
            if abs_v > 500:
                non_silent += 1
            if abs_v > max_amp:
                max_amp = abs_v
            sum_sq += sample * sample
        non_silent_pct = non_silent * 100 // check_count
        rms = int((sum_sq / check_count) ** 0.5)
        logger.info(
            f"[asr] PCM 静音检测 (前 {check_count} 样本): "
            f"非静音={non_silent_pct}%, 最大振幅={max_amp}/32767, RMS={rms}"
        )
        if non_silent_pct < 1 and max_amp < 500:
            logger.warning("[asr] 音频极可能是全静音, 火山 ASR 大概率返回空文本")

    return pcm_bytes


# ============ 主调用 ============

async def recognize(audio_bytes: bytes, mime: str = "") -> AsyncGenerator[Dict[str, Any], None]:
    """
    主入口 (async generator): 输入任意格式音频 bytes (浏览器 webm/opus 通常),
    逐步 yield 事件:
    - {"type": "partial", "text": ..., "duration_ms": ..., "is_last": False}   每次火山返回新文本
    - {"type": "final", "text": ..., "utterances": [...], "duration_ms": ..., "request_id": ..., "log_id": ...}   最后一帧
    异常仍直接 raise (AsrConfigError / AsrError), 上层 async for 会收到.

    同步使用: `async for ev in recognize(...): ... ; ev["type"]=="final" 时拿最终结果`
    SSE 转发: 直接把每个 event 转 SSE 帧推前端
    """
    if not settings.VOLC_ASR_ENABLED:
        raise AsrConfigError("VOLC_ASR_ENABLED=false, 语音识别已禁用")
    if not settings.VOLC_ASR_APP_ID or not settings.VOLC_ASR_ACCESS_TOKEN:
        raise AsrConfigError("VOLC_ASR_APP_ID / VOLC_ASR_ACCESS_TOKEN 未配置")
    if not audio_bytes:
        raise AsrError(-1, "audio_bytes 为空")
    if len(audio_bytes) > settings.VOLC_ASR_MAX_FILE_SIZE_BYTES:
        raise AsrError(-1, f"音频过大 (> {settings.VOLC_ASR_MAX_FILE_SIZE_BYTES // 1024 // 1024} MB)")

    # 1. ffmpeg 转 raw PCM
    pcm_data = _convert_to_pcm_raw(audio_bytes, mime)
    logger.info(
        f"[asr] 音频转码 OK, 输入 {len(audio_bytes)} 字节 ({mime or '?'}), "
        f"输出 {len(pcm_data)} 字节 raw PCM"
    )

    # 2. 切片 (按 .env 配置的 chunk_size_bytes, 默认 3200)
    chunk_size = settings.VOLC_ASR_CHUNK_SIZE_BYTES
    chunks: List[bytes] = []
    for i in range(0, len(pcm_data), chunk_size):
        chunks.append(pcm_data[i:i + chunk_size])
    if not chunks:
        raise AsrError(-1, "音频转码后为空, 无内容可识别")

    # 3. 鉴权 header
    request_id = str(uuid.uuid4())
    headers = {
        "X-Api-App-Key": settings.VOLC_ASR_APP_ID,
        "X-Api-Access-Key": settings.VOLC_ASR_ACCESS_TOKEN,
        "X-Api-Resource-Id": settings.VOLC_ASR_RESOURCE_ID,
        "X-Api-Request-Id": request_id,
        "X-Api-Connect-Id": str(uuid.uuid4()),
    }

    timeout_sec = settings.VOLC_ASR_READ_TIMEOUT_MS / 1000
    connect_timeout_sec = settings.VOLC_ASR_CONNECT_TIMEOUT_MS / 1000

    # 4. 建立 WS 连接 → 发首帧 → 发所有音频 chunks → 收最终结果
    log_id = ""
    final_text = ""
    final_utterances: List[Dict[str, Any]] = []
    final_duration_ms = 0

    async with aiohttp.ClientSession() as session:
        try:
            ws = await session.ws_connect(
                settings.VOLC_ASR_ENDPOINT,
                headers=headers,
                timeout=aiohttp.ClientWSTimeout(ws_receive=timeout_sec, ws_close=10.0),
                heartbeat=15,
            )
        except aiohttp.WSServerHandshakeError as e:
            # 握手失败一般是鉴权问题
            raise AsrError(e.status, f"WebSocket 握手失败: {e.message}")
        except asyncio.TimeoutError:
            raise AsrError(-1, f"WebSocket 连接超时 ({connect_timeout_sec}s)")

        # 拿 X-Tt-Logid (排查用). aiohttp 3.10+ API 改动, 兼容多版本.
        try:
            resp_obj = getattr(ws, "response", None) or getattr(ws, "_response", None)
            if resp_obj is not None and hasattr(resp_obj, "headers"):
                log_id = resp_obj.headers.get("X-Tt-Logid", "")
        except Exception:
            log_id = ""
        logger.info(f"[asr] WS 建立, log_id={log_id or '<unknown>'}, chunks={len(chunks)}")

        try:
            # 4.1 发首帧 (full client request)
            seq = 1
            await ws.send_bytes(_build_full_client_request(seq))
            msg = await asyncio.wait_for(ws.receive(), timeout=timeout_sec)
            if msg.type == aiohttp.WSMsgType.BINARY:
                first_resp = _parse_response(msg.data)
                if first_resp["code"] != 0:
                    payload = first_resp.get("payload_msg") or {}
                    raise AsrError(first_resp["code"], json.dumps(payload, ensure_ascii=False), log_id)

            # 4.2 发所有 audio chunks (不 sleep, 让服务端 backpressure 自己处理)
            total = len(chunks)
            for i, chunk in enumerate(chunks):
                seq += 1
                is_last = (i == total - 1)
                await ws.send_bytes(_build_audio_only_request(seq, chunk, is_last=is_last))

            # 4.3 持续接收, 直到 is_last_package=True 或 error
            frame_idx = 0
            while True:
                try:
                    msg = await asyncio.wait_for(ws.receive(), timeout=timeout_sec)
                except asyncio.TimeoutError:
                    raise AsrError(-1, f"ASR 等待响应超时 ({timeout_sec}s)", log_id)
                if msg.type == aiohttp.WSMsgType.BINARY:
                    resp = _parse_response(msg.data)
                    frame_idx += 1
                    # 打详细日志 (调试用; 拿到 demo 稳定后可改 DEBUG)
                    _payload_str = json.dumps(resp.get("payload_msg"), ensure_ascii=False)[:400]
                    logger.info(
                        f"[asr] frame#{frame_idx} is_last={resp.get('is_last')} "
                        f"code={resp.get('code')} payload={_payload_str}"
                    )
                    if resp["code"] != 0:
                        payload = resp.get("payload_msg") or {}
                        raise AsrError(resp["code"], json.dumps(payload, ensure_ascii=False), log_id)

                    pm = resp.get("payload_msg") or {}
                    # 兼容 result 是 dict 或 list 两种形式
                    raw_result = pm.get("result")
                    candidates: List[Dict[str, Any]] = []
                    if isinstance(raw_result, dict):
                        candidates = [raw_result]
                    elif isinstance(raw_result, list):
                        candidates = [r for r in raw_result if isinstance(r, dict)]
                    text_changed_this_frame = False
                    for cand in candidates:
                        text = (cand.get("text") or "").strip()
                        # 流式累积一般是全量, 取最长那次 (保险)
                        if text and len(text) >= len(final_text):
                            if text != final_text:
                                text_changed_this_frame = True
                            final_text = text
                            final_utterances = cand.get("utterances") or []
                        # 兜底: 从 utterances 拼接 (有时 result.text 为空但 utterances 有内容)
                        utts = cand.get("utterances") or []
                        if not text and utts:
                            joined = "".join(u.get("text", "") for u in utts if isinstance(u, dict))
                            if joined and len(joined) >= len(final_text):
                                if joined != final_text:
                                    text_changed_this_frame = True
                                final_text = joined
                                final_utterances = utts

                    audio_info = pm.get("audio_info") or {}
                    if audio_info.get("duration"):
                        final_duration_ms = int(audio_info["duration"])

                    # 文本有变化就 yield partial 事件 (供前端实时滚动)
                    if text_changed_this_frame:
                        yield {
                            "type": "partial",
                            "text": final_text,
                            "duration_ms": final_duration_ms,
                            "is_last": False,
                        }

                    if resp.get("is_last"):
                        break
                elif msg.type == aiohttp.WSMsgType.CLOSED:
                    logger.info(f"[asr] WS closed by server after {frame_idx} frames")
                    break
                elif msg.type == aiohttp.WSMsgType.ERROR:
                    raise AsrError(-1, f"WS error: {ws.exception()}", log_id)
        finally:
            if not ws.closed:
                await ws.close()

    if not final_text:
        raise AsrError(-1, "ASR 未识别到任何文本 (空音频或全静音)", log_id)

    logger.info(
        f"[asr] 识别完成, 文本 {len(final_text)} 字, "
        f"{len(final_utterances)} 分句, {final_duration_ms} ms"
    )

    yield {
        "type": "final",
        "text": final_text,
        "utterances": final_utterances,
        "duration_ms": final_duration_ms,
        "request_id": request_id,
        "log_id": log_id,
    }


# ============ 真·实时流式 (浏览器 AudioWorklet 直推 16k PCM) ============

async def recognize_streaming(
    pcm_queue: "asyncio.Queue[Optional[bytes]]",
) -> AsyncGenerator[Dict[str, Any], None]:
    """
    真·实时流式识别: 输入是一个 PCM 队列 (浏览器 AudioWorklet 实时采集的 16kHz/16bit/mono raw PCM).
    与 recognize() 区别:
    - 不走 ffmpeg (浏览器已给 16k PCM, 直接送火山)
    - 不等录完, 边收 PCM 边发火山, 火山边识别边回, 实现"说一半就出字"
    - pcm_queue 收到 None 哨兵 = 用户停止说话, 发最后一帧 (负 seq) 收尾

    逐步 yield:
    - {"type": "partial", "text": ..., "duration_ms": ..., "is_last": False}
    - {"type": "final", "text": ..., "utterances": [...], "duration_ms": ..., "request_id": ..., "log_id": ...}
    异常直接 raise (AsrConfigError / AsrError).
    """
    if not settings.VOLC_ASR_ENABLED:
        raise AsrConfigError("VOLC_ASR_ENABLED=false, 语音识别已禁用")
    if not settings.VOLC_ASR_APP_ID or not settings.VOLC_ASR_ACCESS_TOKEN:
        raise AsrConfigError("VOLC_ASR_APP_ID / VOLC_ASR_ACCESS_TOKEN 未配置")

    chunk_size = settings.VOLC_ASR_CHUNK_SIZE_BYTES  # 默认 3200 = 100ms @ 16k/16bit/mono
    request_id = str(uuid.uuid4())
    headers = {
        "X-Api-App-Key": settings.VOLC_ASR_APP_ID,
        "X-Api-Access-Key": settings.VOLC_ASR_ACCESS_TOKEN,
        "X-Api-Resource-Id": settings.VOLC_ASR_RESOURCE_ID,
        "X-Api-Request-Id": request_id,
        "X-Api-Connect-Id": str(uuid.uuid4()),
    }
    timeout_sec = settings.VOLC_ASR_READ_TIMEOUT_MS / 1000
    connect_timeout_sec = settings.VOLC_ASR_CONNECT_TIMEOUT_MS / 1000

    log_id = ""
    final_text = ""
    final_utterances: List[Dict[str, Any]] = []
    final_duration_ms = 0

    async with aiohttp.ClientSession() as session:
        try:
            ws = await session.ws_connect(
                settings.VOLC_ASR_ENDPOINT,
                headers=headers,
                timeout=aiohttp.ClientWSTimeout(ws_receive=timeout_sec, ws_close=10.0),
                heartbeat=15,
            )
        except aiohttp.WSServerHandshakeError as e:
            raise AsrError(e.status, f"WebSocket 握手失败: {e.message}")
        except asyncio.TimeoutError:
            raise AsrError(-1, f"WebSocket 连接超时 ({connect_timeout_sec}s)")

        try:
            resp_obj = getattr(ws, "response", None) or getattr(ws, "_response", None)
            if resp_obj is not None and hasattr(resp_obj, "headers"):
                log_id = resp_obj.headers.get("X-Tt-Logid", "")
        except Exception:
            log_id = ""
        logger.info(f"[asr-stream] WS 建立, log_id={log_id or '<unknown>'}")

        # sender task: 从 pcm_queue 读 PCM, 攒够 chunk_size 发一帧, None 哨兵发最后一帧
        sender_error: Dict[str, Any] = {}

        async def _sender() -> None:
            seq = 1  # 与首帧 (full request, seq=1) 对齐, 音频帧从 2 起
            buf = bytearray()
            try:
                while True:
                    chunk = await pcm_queue.get()
                    if chunk is None:
                        # 结束哨兵: 把剩余 buf 作为最后一帧发出 (负 seq), buf 可能为空
                        seq += 1
                        await ws.send_bytes(
                            _build_audio_only_request(seq, bytes(buf), is_last=True)
                        )
                        return
                    buf.extend(chunk)
                    while len(buf) >= chunk_size:
                        seq += 1
                        await ws.send_bytes(
                            _build_audio_only_request(seq, bytes(buf[:chunk_size]), is_last=False)
                        )
                        del buf[:chunk_size]
            except Exception as e:  # noqa: BLE001
                sender_error["err"] = e
                logger.warning(f"[asr-stream] sender 异常: {e}")

        sender_task: Optional[asyncio.Task] = None
        try:
            # 发首帧 (full client request, seq=1), 等首响应确认配置 OK
            await ws.send_bytes(_build_full_client_request(1))
            first_msg = await asyncio.wait_for(ws.receive(), timeout=timeout_sec)
            if first_msg.type == aiohttp.WSMsgType.BINARY:
                first_resp = _parse_response(first_msg.data)
                if first_resp["code"] != 0:
                    payload = first_resp.get("payload_msg") or {}
                    raise AsrError(first_resp["code"], json.dumps(payload, ensure_ascii=False), log_id)

            # 起 sender, 开始边收 PCM 边发
            sender_task = asyncio.create_task(_sender())

            frame_idx = 0
            while True:
                try:
                    msg = await asyncio.wait_for(ws.receive(), timeout=timeout_sec)
                except asyncio.TimeoutError:
                    raise AsrError(-1, f"ASR 等待响应超时 ({timeout_sec}s)", log_id)

                if msg.type == aiohttp.WSMsgType.BINARY:
                    resp = _parse_response(msg.data)
                    frame_idx += 1
                    if resp["code"] != 0:
                        payload = resp.get("payload_msg") or {}
                        raise AsrError(resp["code"], json.dumps(payload, ensure_ascii=False), log_id)

                    pm = resp.get("payload_msg") or {}
                    raw_result = pm.get("result")
                    candidates: List[Dict[str, Any]] = []
                    if isinstance(raw_result, dict):
                        candidates = [raw_result]
                    elif isinstance(raw_result, list):
                        candidates = [r for r in raw_result if isinstance(r, dict)]
                    text_changed = False
                    for cand in candidates:
                        text = (cand.get("text") or "").strip()
                        if text and len(text) >= len(final_text):
                            if text != final_text:
                                text_changed = True
                            final_text = text
                            final_utterances = cand.get("utterances") or []
                        utts = cand.get("utterances") or []
                        if not text and utts:
                            joined = "".join(u.get("text", "") for u in utts if isinstance(u, dict))
                            if joined and len(joined) >= len(final_text):
                                if joined != final_text:
                                    text_changed = True
                                final_text = joined
                                final_utterances = utts

                    audio_info = pm.get("audio_info") or {}
                    if audio_info.get("duration"):
                        final_duration_ms = int(audio_info["duration"])

                    if text_changed:
                        yield {
                            "type": "partial",
                            "text": final_text,
                            "duration_ms": final_duration_ms,
                            "is_last": False,
                        }

                    if resp.get("is_last"):
                        break
                elif msg.type == aiohttp.WSMsgType.CLOSED:
                    logger.info(f"[asr-stream] WS closed by server after {frame_idx} frames")
                    break
                elif msg.type == aiohttp.WSMsgType.ERROR:
                    raise AsrError(-1, f"WS error: {ws.exception()}", log_id)

            # 收尾: 等 sender 结束 (一般此时已发完最后一帧)
            if sender_task is not None:
                try:
                    await asyncio.wait_for(sender_task, timeout=5.0)
                except asyncio.TimeoutError:
                    sender_task.cancel()
            if sender_error.get("err"):
                logger.warning(f"[asr-stream] sender 报告异常 (已忽略, 主流程已拿到结果): {sender_error['err']}")
        finally:
            if sender_task is not None and not sender_task.done():
                sender_task.cancel()
            if not ws.closed:
                await ws.close()

    if not final_text:
        raise AsrError(-1, "ASR 未识别到任何文本 (空音频或全静音)", log_id)

    logger.info(
        f"[asr-stream] 识别完成, 文本 {len(final_text)} 字, "
        f"{len(final_utterances)} 分句, {final_duration_ms} ms"
    )
    yield {
        "type": "final",
        "text": final_text,
        "utterances": final_utterances,
        "duration_ms": final_duration_ms,
        "request_id": request_id,
        "log_id": log_id,
    }
