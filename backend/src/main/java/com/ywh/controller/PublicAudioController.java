package com.ywh.controller;

import com.ywh.service.quick.AudioStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开音频服务（免鉴权，见 SecurityConfig）。供豆包(火山)匿名拉取上传/录制的音频。
 * 仅当本地存储 + 后端公网可达时有意义；用 TOS 时音频直接走对象存储直链，不经过这里。
 */
@RestController
@RequestMapping("/api/quick-audio")
@RequiredArgsConstructor
public class PublicAudioController {

    private final AudioStorageService storage;

    @GetMapping("/{filename}")
    public ResponseEntity<byte[]> serve(@PathVariable String filename) {
        byte[] data = storage.load(filename);
        return ResponseEntity.ok()
                .contentType(contentType(filename))
                .contentLength(data.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(data);
    }

    @RequestMapping(value = "/{filename}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> head(@PathVariable String filename) {
        byte[] data = storage.load(filename);
        return ResponseEntity.ok()
                .contentType(contentType(filename))
                .contentLength(data.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .build();
    }

    private MediaType contentType(String filename) {
        String f = filename.toLowerCase();
        if (f.endsWith(".mp3")) return MediaType.parseMediaType("audio/mpeg");
        if (f.endsWith(".wav")) return MediaType.parseMediaType("audio/wav");
        if (f.endsWith(".m4a") || f.endsWith(".aac")) return MediaType.parseMediaType("audio/aac");
        if (f.endsWith(".ogg")) return MediaType.parseMediaType("audio/ogg");
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
