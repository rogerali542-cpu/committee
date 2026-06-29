package com.ywh.service.quick;

import com.ywh.dto.quick.AsrResult;
import com.ywh.dto.quick.AsrTaskVO;
import com.ywh.service.CommitteeService;

/**
 * 语音识别服务（豆包 ASR 接入点）。
 * 录音转写是异步的：submit 提交后返回 taskId，前端轮询 status，done 后取 result。
 */
public interface AsrService {

    /** @deprecated 旧两参，提交已上传的录音做转写。 */
    AsrTaskVO submit(Long meetingId, String audioRef);

    /** 提交指定录音记录做转写，返回异步任务。完成后更新 MeetingRecording.asrStatus=done。 */
    AsrTaskVO submit(Long meetingId, Long recordingId, CommitteeService committeeService);

    /** 查询转写任务状态。 */
    AsrTaskVO status(String taskId);

    /** 取转写结果（status=done 时有效）。 */
    AsrResult result(Long meetingId);

    /** 删除某条录音后，清掉它已缓存的逐条转写结果，使合并结果不再包含它。默认空实现。 */
    default void evictRecording(Long meetingId, Long recordingId) {}
}
