package com.ywh.service.quick;

/**
 * 音频存储。小程序录音/上传的音频落地后，返回一个【豆包(火山)可拉取的公网 URL】。
 * 默认本地实现；生产可换 TOS/OSS 实现（接口不变）。
 */
public interface AudioStorageService {

    /** 保存音频，返回公网可访问 URL。 */
    String save(Long meetingId, byte[] data, String ext);

    /** 读取已保存音频（供本地实现的对外 GET 服务用）。 */
    byte[] load(String filename);

    /**
     * 是否为远端对象存储（音频不落本机后端，豆包直接从云端公网 URL 拉取）。
     * 远端实现（TOS/OSS）返回 true —— ASR 提交时无需把音频读回内联 Base64，直接给 URL。
     */
    default boolean isRemote() {
        return false;
    }
}
