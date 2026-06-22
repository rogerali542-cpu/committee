package com.ywh.service.quick;

import com.ywh.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 本地磁盘音频存储。落盘到 storage.audio.dir，URL 指回后端的公开 GET 接口
 * （/api/quick-audio/{filename}，见 PublicAudioController）。
 *
 * ⚠️ 仅当后端公网可达时豆包才能拉到。局域网部署请改用 TOS/OSS：
 *    新建一个实现本接口的 @Service（@Primary 或加开关），save() 里传对象存储并返回直链。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "storage.audio", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalAudioStorageService implements AudioStorageService {

    private final StorageProperties props;

    @Override
    public String save(Long meetingId, byte[] data, String ext) {
        try {
            Path dir = Paths.get(props.getDir());
            Files.createDirectories(dir);
            String filename = meetingId + "_" + UUID.randomUUID().toString().substring(0, 8)
                    + "." + (ext == null || ext.isBlank() ? "mp3" : ext);
            Files.write(dir.resolve(filename), data);
            String base = props.getPublicBaseUrl() == null ? "" : props.getPublicBaseUrl().replaceAll("/+$", "");
            return base + "/api/quick-audio/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("音频保存失败: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] load(String filename) {
        try {
            // 防目录穿越：只取文件名部分
            String safe = Paths.get(filename).getFileName().toString();
            return Files.readAllBytes(Paths.get(props.getDir()).resolve(safe));
        } catch (IOException e) {
            throw new RuntimeException("音频读取失败: " + filename, e);
        }
    }
}
