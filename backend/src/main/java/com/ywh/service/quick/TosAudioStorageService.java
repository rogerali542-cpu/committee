package com.ywh.service.quick;

import com.ywh.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * TOS/S3 兼容对象存储音频实现。
 *
 * 目标：录音上传后直接返回对象存储公网 URL，豆包 ASR 可从云端拉取，
 * 不再依赖本机内网地址或 cloudflared 临时隧道。
 *
 * 前提：bucket 对象需可被豆包匿名 GET 访问，或 public-base-url 指向可访问的 CDN/签名网关。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "storage.audio", name = "type", havingValue = "tos")
public class TosAudioStorageService implements AudioStorageService {

    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final HexFormat HEX = HexFormat.of();

    private final StorageProperties props;
    private final HttpClient http = HttpClient.newBuilder().build();

    @Override
    public String save(Long meetingId, byte[] data, String ext) {
        validateConfig();
        try {
            String objectKey = objectKey(meetingId, ext);
            URI endpoint = URI.create(trimRightSlash(props.getEndpoint()));
            String host = props.getBucket() + "." + endpoint.getHost();
            String scheme = endpoint.getScheme() == null ? "https" : endpoint.getScheme();
            URI uri = URI.create(scheme + "://" + host + "/" + encodePath(objectKey));

            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
            String amzDate = AMZ_DATE.format(now);
            String date = DATE.format(now);
            String payloadHash = sha256Hex(data);
            String contentType = contentType(objectKey);

            String canonicalHeaders = ""
                    + "host:" + host + "\n"
                    + "x-amz-content-sha256:" + payloadHash + "\n"
                    + "x-amz-date:" + amzDate + "\n";
            String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
            String canonicalRequest = "PUT\n"
                    + "/" + encodePath(objectKey) + "\n"
                    + "\n"
                    + canonicalHeaders + "\n"
                    + signedHeaders + "\n"
                    + payloadHash;
            String scope = date + "/" + props.getRegion() + "/" + props.getSigningService() + "/request";
            String stringToSign = "AWS4-HMAC-SHA256\n"
                    + amzDate + "\n"
                    + scope + "\n"
                    + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            String signature = hmacHex(signingKey(date), stringToSign);
            String authorization = "AWS4-HMAC-SHA256 Credential=" + props.getAccessKey() + "/" + scope
                    + ", SignedHeaders=" + signedHeaders
                    + ", Signature=" + signature;

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Content-Type", contentType)
                    .header("Host", host)
                    .header("x-amz-content-sha256", payloadHash)
                    .header("x-amz-date", amzDate)
                    .header("Authorization", authorization)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("对象存储上传失败 status=" + response.statusCode() + " body=" + response.body());
            }

            String base = props.getPublicBaseUrl();
            if (base == null || base.isBlank()) {
                base = scheme + "://" + host;
            }
            return trimRightSlash(base) + "/" + encodePath(objectKey);
        } catch (Exception e) {
            throw new RuntimeException("对象存储音频保存失败: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] load(String filename) {
        throw new UnsupportedOperationException("TOS 音频不经过本地后端读取");
    }

    private void validateConfig() {
        if (isBlank(props.getEndpoint())) throw new IllegalStateException("storage.audio.endpoint 未配置");
        if (isBlank(props.getRegion())) throw new IllegalStateException("storage.audio.region 未配置");
        if (isBlank(props.getBucket())) throw new IllegalStateException("storage.audio.bucket 未配置");
        if (isBlank(props.getAccessKey())) throw new IllegalStateException("storage.audio.access-key 未配置");
        if (isBlank(props.getSecretKey())) throw new IllegalStateException("storage.audio.secret-key 未配置");
        if (isBlank(props.getSigningService())) throw new IllegalStateException("storage.audio.signing-service 未配置");
    }

    private String objectKey(Long meetingId, String ext) {
        String suffix = isBlank(ext) ? "mp3" : ext.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (suffix.isBlank()) suffix = "mp3";
        String prefix = props.getKeyPrefix() == null ? "" : props.getKeyPrefix().replaceAll("^/+|/+$", "");
        String name = meetingId + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + suffix;
        return prefix.isBlank() ? name : prefix + "/" + name;
    }

    private byte[] signingKey(String date) throws Exception {
        byte[] kDate = hmac(("AWS4" + props.getSecretKey()).getBytes(StandardCharsets.UTF_8), date);
        byte[] kRegion = hmac(kDate, props.getRegion());
        byte[] kService = hmac(kRegion, props.getSigningService());
        return hmac(kService, "request");
    }

    private static byte[] hmac(byte[] key, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacHex(byte[] key, String value) throws Exception {
        return HEX.formatHex(hmac(key, value));
    }

    private static String sha256Hex(byte[] data) throws Exception {
        return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static String encodePath(String raw) {
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (isUnreserved(c) || c == '/') sb.append(c);
            else {
                byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                for (byte b : bytes) sb.append('%').append(String.format("%02X", b));
            }
        }
        return sb.toString();
    }

    private static boolean isUnreserved(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '-' || c == '_' || c == '.' || c == '~';
    }

    private static String trimRightSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String contentType(String filename) {
        String f = filename.toLowerCase(Locale.ROOT);
        if (f.endsWith(".mp3")) return MediaType.parseMediaType("audio/mpeg").toString();
        if (f.endsWith(".wav")) return MediaType.parseMediaType("audio/wav").toString();
        if (f.endsWith(".m4a") || f.endsWith(".aac")) return MediaType.parseMediaType("audio/aac").toString();
        if (f.endsWith(".ogg")) return MediaType.parseMediaType("audio/ogg").toString();
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
