package com.ywh.service.quick;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ywh.config.DoubaoProperties;
import com.ywh.entity.MeetingMaterial;
import com.ywh.repository.MeetingMaterialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 会议材料 OCR 文字提取。把上传的图片/PDF 交给独立的 ocr-asr-service（FastAPI /v1/ocr/document）：
 * 该服务负责 PDF 逐页渲染 + 豆包视觉模型识别，返回整份材料的全文文字，本类只发文件、收文字、落库。
 *
 * 设计：上传材料后异步触发，先把材料标记 processing，后台 HTTP 调用完成后写入 ocrText 并标记 done/failed。
 * 任何失败都只记 failed + 日志，绝不抛出影响材料上传本身（OCR 只是给 AI 生成纪要的补充语料）。
 * 仅当 doubao.ocr.enabled=true 时此 Bean 才存在；否则 CommitteeService 注入到的 ObjectProvider 取不到，OCR 自动跳过。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "doubao.ocr", name = "enabled", havingValue = "true")
public class DoubaoOcrService {

    private final DoubaoProperties props;
    private final ObjectMapper mapper;
    private final AudioStorageService audioStorage;   // 复用：材料与音频同一套本地/对象存储
    private final MeetingMaterialRepository materialRepo;

    // 支持 OCR 的文件类型（其余如 docx/xlsx 不识别，状态保持 null）
    private static final Set<String> OCR_EXTS = Set.of(
            "pdf", "png", "jpg", "jpeg", "gif", "webp", "bmp");

    private final HttpClient http = HttpClient.newBuilder()
            // 强制 HTTP/1.1：默认 HTTP/2 会先发 h2c 明文升级握手，uvicorn(h11) 不支持，
            // 升级失败会破坏 multipart 请求体，导致 OCR 服务收不到 file 字段（422 Field required）。
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10)).build();

    /**
     * 触发某份材料的 OCR（幂等性由调用方保证，一般在 addMaterial 后调一次）。
     * 不可识别的类型直接返回、不改状态；可识别的先标 processing 再后台识别。
     */
    public void submitMaterialOcr(Long materialId) {
        if (materialId == null) return;
        MeetingMaterial mat = materialRepo.findById(materialId).orElse(null);
        if (mat == null) return;

        if (!isOcrable(mat)) {
            log.debug("[OCR] 跳过非图片/PDF 材料 id={} type={} url={}", materialId, mat.getFileType(), mat.getFileUrl());
            return;
        }
        String filename = extractFilename(mat.getFileUrl());
        if (filename == null) {
            log.warn("[OCR] 材料无有效 fileUrl，跳过 id={}", materialId);
            return;
        }

        markStatus(materialId, "processing", null);

        final DoubaoProperties.Ocr cfg = props.getOcr();
        CompletableFuture.runAsync(() -> {
            try {
                byte[] bytes = loadBytes(filename, mat.getFileUrl());
                if (bytes == null || bytes.length == 0) {
                    log.warn("[OCR] 材料文件读取为空 id={} file={}", materialId, filename);
                    markStatus(materialId, "failed", null);
                    return;
                }
                String text = callOcrDocument(cfg, filename, mat.getFileType(), bytes);
                markStatus(materialId, "done", text != null ? text : "");
                log.info("[OCR] 识别完成 id={} file={} chars={}", materialId, filename,
                        text == null ? 0 : text.length());
            } catch (Exception e) {
                log.warn("[OCR] 识别失败 id={} file={}: {}", materialId, filename, e.getMessage());
                markStatus(materialId, "failed", null);
            }
        });
    }

    // ── 调用 ocr-asr-service /v1/ocr/document（multipart 上传单个文件，收 JSON.text）──
    private String callOcrDocument(DoubaoProperties.Ocr cfg, String filename, String fileType, byte[] data)
            throws Exception {
        String base = cfg.getBaseUrl() == null ? "" : cfg.getBaseUrl().replaceAll("/+$", "");
        String boundary = "----ywhOcr" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildMultipart(boundary, "file", filename, partContentType(filename, fileType), data);

        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(base + "/v1/ocr/document"))
                .timeout(Duration.ofSeconds(Math.max(30, cfg.getReadTimeoutSeconds())))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (cfg.getInternalToken() != null && !cfg.getInternalToken().isBlank()) {
            rb.header("X-Internal-Token", cfg.getInternalToken());
        }

        HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("OCR 服务返回 " + resp.statusCode() + ": " + truncate(resp.body(), 300));
        }
        JsonNode root = mapper.readTree(resp.body());
        return root.path("text").asText("");
    }

    private byte[] loadBytes(String filename, String fileUrl) {
        // 优先本地存储（默认实现）；取不到再按 URL 兜底拉取（对象存储/外链场景）。
        try {
            byte[] b = audioStorage.load(filename);
            if (b != null && b.length > 0) return b;
        } catch (Exception e) {
            log.debug("[OCR] 本地读取失败，改用 URL 拉取 file={}: {}", filename, e.getMessage());
        }
        if (fileUrl != null && (fileUrl.startsWith("http://") || fileUrl.startsWith("https://"))) {
            try {
                HttpResponse<byte[]> r = http.send(
                        HttpRequest.newBuilder().uri(URI.create(fileUrl))
                                .timeout(Duration.ofSeconds(30)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() == 200) return r.body();
            } catch (Exception e) {
                log.debug("[OCR] URL 拉取失败 url={}: {}", fileUrl, e.getMessage());
            }
        }
        return null;
    }

    /** 落库材料 OCR 状态/文字。失败仅记日志，不影响主流程。 */
    private void markStatus(Long materialId, String status, String text) {
        try {
            MeetingMaterial m = materialRepo.findById(materialId).orElse(null);
            if (m == null) return;
            m.setOcrStatus(status);
            if (text != null) m.setOcrText(text);
            materialRepo.save(m);
        } catch (Exception e) {
            log.warn("[OCR] 更新材料 OCR 状态失败 id={} status={}: {}", materialId, status, e.getMessage());
        }
    }

    private boolean isOcrable(MeetingMaterial mat) {
        String ext = extOf(mat.getFileType());
        if (ext != null && OCR_EXTS.contains(ext)) return true;
        // fileType 不规范时再看 URL 后缀
        String urlExt = extFromName(extractFilename(mat.getFileUrl()));
        return urlExt != null && OCR_EXTS.contains(urlExt);
    }

    /** fileType 可能是 "pdf" / "图片" / "image/png" 等，尽量归一到小写扩展名。 */
    private String extOf(String fileType) {
        if (fileType == null) return null;
        String t = fileType.toLowerCase().trim();
        if (t.contains("pdf")) return "pdf";
        if (t.contains("png")) return "png";
        if (t.contains("jpeg") || t.contains("jpg")) return "jpg";
        if (t.contains("gif")) return "gif";
        if (t.contains("webp")) return "webp";
        if (t.contains("bmp")) return "bmp";
        if (OCR_EXTS.contains(t)) return t;        // 本就是裸扩展名
        return null;
    }

    private String partContentType(String filename, String fileType) {
        String ext = extFromName(filename);
        if (ext == null) ext = extOf(fileType);
        if (ext == null) return "application/octet-stream";
        switch (ext) {
            case "pdf": return "application/pdf";
            case "png": return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "bmp": return "image/bmp";
            default: return "application/octet-stream";
        }
    }

    private byte[] buildMultipart(String boundary, String field, String filename,
                                  String contentType, byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + field + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /** 从 URL/路径稳健提取文件名（兼容 query/fragment/裸名），如 .../quick-audio/0_abc.pdf → 0_abc.pdf。 */
    private String extractFilename(String url) {
        if (url == null || url.isBlank()) return null;
        String s = url;
        int q = s.indexOf('?'); if (q >= 0) s = s.substring(0, q);
        int h = s.indexOf('#'); if (h >= 0) s = s.substring(0, h);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        int slash = s.lastIndexOf('/');
        String name = slash >= 0 ? s.substring(slash + 1) : s;
        return name.isBlank() ? null : name;
    }

    private String extFromName(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return null;
        return name.substring(dot + 1).toLowerCase();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
