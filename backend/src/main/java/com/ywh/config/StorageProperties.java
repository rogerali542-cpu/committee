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
    /** 本地落盘目录。 */
    private String dir = "./data/audio";
    /** 对外公网根地址，拼成豆包可拉取的音频 URL。必须火山引擎可访问。 */
    private String publicBaseUrl;
}
