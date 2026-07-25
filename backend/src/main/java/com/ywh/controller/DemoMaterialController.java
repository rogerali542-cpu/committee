package com.ywh.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * 本地演示材料预览通道。仅在目录存在时提供文件，不参与正式上传或归档。
 */
@RestController
@RequestMapping("/api/demo-materials")
public class DemoMaterialController {

    private final Path root;

    public DemoMaterialController(
            @Value("${demo.materials.dir:../test-data/阳光花园2026上半年}") String directory) {
        this.root = Paths.get(directory).toAbsolutePath().normalize();
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<FileSystemResource> serve(@PathVariable String filename) throws IOException {
        String safeName = Paths.get(filename).getFileName().toString();
        Path file;
        try (Stream<Path> paths = Files.walk(root)) {
            file = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(safeName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("演示材料不存在：" + safeName));
        }
        return ResponseEntity.ok()
                .contentType(contentType(safeName))
                .contentLength(Files.size(file))
                .body(new FileSystemResource(file));
    }

    private MediaType contentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
