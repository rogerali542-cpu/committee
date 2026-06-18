package com.ywh.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 豆包（火山引擎）配置。绑定 application.yml 的 doubao.* —— 值用环境变量覆盖，勿硬编码密钥。
 */
@Data
@Component
@ConfigurationProperties(prefix = "doubao")
public class DoubaoProperties {

    private Asr asr = new Asr();
    private Llm llm = new Llm();
    private Embedding embedding = new Embedding();

    /** 录音文件识别大模型-标准版（volc.bigasr.auc）。 */
    @Data
    public static class Asr {
        private boolean enabled = true;
        private String appKey;
        private String accessToken;
        private String secretKey;      // 标准版 header 鉴权用不到，留作备用
        private String resourceId = "volc.bigasr.auc";
        private String modelName = "bigmodel";
        private String submitUrl;
        private String queryUrl;
        private String defaultFormat = "mp3";
    }

    /** 豆包大模型（方舟 Ark），用于纪要整理/润色——与 ASR 是两套凭证。 */
    @Data
    public static class Llm {
        private boolean enabled = false;
        private String apiKey;
        private String model;
        private String baseUrl;
    }

    /** 豆包多模态向量化（方舟 Ark embedding），用于检索/RAG——与 LLM 共用 Ark API Key。 */
    @Data
    public static class Embedding {
        private boolean enabled = false;
        private String apiKey;
        private String url = "https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal";
        private String model = "doubao-embedding-vision-251215";
        private int dimensions = 1024;
    }
}
