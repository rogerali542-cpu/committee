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
}
