package com.ywh.controller;

import com.ywh.annotation.RequireRole;
import com.ywh.service.quick.AudioStorageService;
import com.ywh.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 通用文件上传接口。前端选文件后传这里拿到公网 URL，再带 URL 调各业务"添加"接口
 * （议题/学习/接待佐证、会议材料、补充归档等）。复用 AudioStorageService 落地存储，
 * 对扩展名无音频硬假设（图片/PDF 通用）。文件经 /api/quick-audio/{filename} 提供。
 */
@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AudioStorageService audioStorage;

    @PostMapping("/upload")
    @RequireRole({"主任", "副主任", "记录员", "委员"})
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return Result.fail("文件为空");
        }
        String ext = extractExt(file.getOriginalFilename());
        byte[] data = file.getBytes();
        String url = audioStorage.save(0L, data, ext); // 0L 作通用前缀
        return Result.ok(Map.of(
                "url", url,
                "fileName", file.getOriginalFilename() != null ? file.getOriginalFilename() : "",
                "fileType", ext,
                "fileSize", file.getSize()
        ));
    }

    private String extractExt(String filename) {
        if (filename == null) return "bin";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && dot < filename.length() - 1 ? filename.substring(dot + 1).toLowerCase() : "bin";
    }
}
