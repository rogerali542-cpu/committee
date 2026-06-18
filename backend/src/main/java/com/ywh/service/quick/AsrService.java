package com.ywh.service.quick;

import com.ywh.dto.quick.AsrResult;
import com.ywh.dto.quick.AsrTaskVO;

/**
 * 语音识别服务（豆包 ASR 接入点）。
 * 录音转写是异步的：submit 提交后返回 taskId，前端轮询 status，done 后取 result。
 */
public interface AsrService {

    /** 提交一段已上传的会议录音做转写，返回异步任务。audioRef = 对象存储 key / uploadId。 */
    AsrTaskVO submit(Long meetingId, String audioRef);

    /** 查询转写任务状态。 */
    AsrTaskVO status(String taskId);

    /** 取转写结果（status=done 时有效）。 */
    AsrResult result(Long meetingId);
}
