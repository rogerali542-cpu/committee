package com.ywh.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 音频存储配置（storage.audio.*）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage.audio")
public class StorageProperties {
    /** local=本地磁盘；tos=TOS/S3 兼容对象存储。 */
    private String type = "local";
    /** 本地落盘目录。 */
    private String dir = "./data/audio";
    /** 对外公网根地址，拼成豆包可拉取的音频 URL。必须火山引擎可访问。 */
    private String publicBaseUrl;

    /** TOS/S3 endpoint，例如 https://tos-cn-beijing.volces.com。 */
    private String endpoint;
    /** TOS/S3 region，例如 cn-beijing。 */
    private String region = "cn-beijing";
    /** Bucket 名称。 */
    private String bucket;
    /** Access Key。 */
    private String accessKey;
    /** Secret Key。 */
    private String secretKey;
    /** 对象 key 前缀。 */
    private String keyPrefix = "quick-audio";
    /** 签名服务名：TOS 常用 tos；S3 兼容可改为 s3。 */
    private String signingService = "tos";
}
