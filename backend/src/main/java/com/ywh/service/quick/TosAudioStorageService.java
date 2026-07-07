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

            // 对象级公共读：只让本音频对象可匿名 GET（供豆包拉取），不必把整桶设为公共读。
            // 规范头必须按字典序排列：host < x-amz-acl < x-amz-content-sha256 < x-amz-date。
            String acl = "public-read";
            String canonicalHeaders = ""
                    + "host:" + host + "\n"
                    + "x-amz-acl:" + acl + "\n"
                    + "x-amz-content-sha256:" + payloadHash + "\n"
                    + "x-amz-date:" + amzDate + "\n";
            String signedHeaders = "host;x-amz-acl;x-amz-content-sha256;x-amz-date";
            String canonicalRequest = "PUT\n"
                    + "/" + encodePath(objectKey) + "\n"
                    + "\n"
                    + canonicalHeaders + "\n"
                    + signedHeaders + "\n"
                    + payloadHash;
            // SigV4 终止符固定为 aws4_request（火山 TOS 的 S3 兼容端点强校验此值）。
            String scope = date + "/" + props.getRegion() + "/" + props.getSigningService() + "/aws4_request";
            String stringToSign = "AWS4-HMAC-SHA256\n"
                    + amzDate + "\n"
                    + scope + "\n"
                    + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            String signature = hmacHex(signingKey(date), stringToSign);
            String authorization = "AWS4-HMAC-SHA256 Credential=" + props.getAccessKey() + "/" + scope
                    + ", SignedHeaders=" + signedHeaders
                    + ", Signature=" + signature;

            // 不能手动设 Host：java.net.http 把 Host 列为受限头，setHeader 会抛
            // IllegalArgumentException。HttpClient 会自动按 URI 主机名发 Host，正好等于
            // 我们签名里用的 host（committee.tos-s3-...），签名仍然对得上。
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Content-Type", contentType)
                    .header("x-amz-acl", acl)
                    .header("x-amz-content-sha256", payloadHash)
                    .header("x-amz-date", amzDate)
                    .header("Authorization", authorization)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("对象存储上传失败 status=" + response.statusCode() + " body=" + response.body());
            }

            // public-base-url 仅在指向【公网可达的 CDN/网关】时才用它拼直链；
            // 若为空或还残留 localhost/内网默认值（豆包拉不到），回退到 bucket 虚拟主机直链。
            String base = props.getPublicBaseUrl();
            if (base == null || base.isBlank() || isPrivateHost(base)) {
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

    @Override
    public boolean isRemote() {
        return true; // 音频在 TOS，豆包直接按公网 URL 拉取，不必读回内联
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
        return hmac(kService, "aws4_request");
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

    /** 该 URL 的主机是否为 localhost/内网（豆包等公网服务拉不到）。 */
    private static boolean isPrivateHost(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            if (host == null) return true;
            host = host.toLowerCase(Locale.ROOT);
            if ("localhost".equals(host) || "127.0.0.1".equals(host) || host.startsWith("192.168.")
                    || host.startsWith("10.")) return true;
            return host.matches("172\\.(1[6-9]|2\\d|3[0-1])\\..*");
        } catch (Exception e) {
            return false; // 解析不了就不当内网，交给上传时报错
        }
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
