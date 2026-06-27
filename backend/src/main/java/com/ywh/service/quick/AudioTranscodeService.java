package com.ywh.service.quick;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import java.io.File;
import java.nio.file.Files;

/**
 * 音频转码：把浏览器 H5 录音（Chrome/安卓 webm/opus、iOS mp4/m4a/aac 等）转成
 * 豆包录音文件识别 (volc.bigasr.auc) 友好的 16k 单声道 mp3。
 * 用 jave2（ws.schild:jave-all-deps，内置各平台 ffmpeg 二进制），无需在服务器单独安装 ffmpeg。
 */
@Slf4j
@Service
public class AudioTranscodeService {

    /** 转成 16kHz / 单声道 / mp3。input 为原始音频字节，sourceExt 为原扩展名（用于临时文件后缀，帮助 ffmpeg 识别容器）。 */
    public byte[] toMono16kMp3(byte[] input, String sourceExt) throws Exception {
        String srcSuffix = (sourceExt == null || sourceExt.isBlank()) ? "bin" : sourceExt.toLowerCase();
        File src = File.createTempFile("asr-src-", "." + srcSuffix);
        File dst = File.createTempFile("asr-dst-", ".mp3");
        try {
            Files.write(src.toPath(), input);

            AudioAttributes audio = new AudioAttributes();
            audio.setCodec("libmp3lame");
            audio.setChannels(1);
            audio.setSamplingRate(16000);
            audio.setBitRate(64000);

            EncodingAttributes attrs = new EncodingAttributes();
            attrs.setOutputFormat("mp3");
            attrs.setAudioAttributes(audio);

            new Encoder().encode(new MultimediaObject(src), dst, attrs);

            byte[] out = Files.readAllBytes(dst.toPath());
            log.info("[transcode] {} bytes ({}) -> {} bytes (16k mono mp3)",
                    input.length, srcSuffix, out.length);
            return out;
        } finally {
            try { Files.deleteIfExists(src.toPath()); } catch (Exception ignore) {}
            try { Files.deleteIfExists(dst.toPath()); } catch (Exception ignore) {}
        }
    }
}
