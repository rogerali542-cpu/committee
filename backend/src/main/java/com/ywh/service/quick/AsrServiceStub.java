package com.ywh.service.quick;

import com.ywh.dto.quick.AsrResult;
import com.ywh.dto.quick.AsrTaskVO;
import com.ywh.service.CommitteeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 豆包 ASR 的【桩实现】——当前返回固定的假转写，让前后端流程先跑通。
 *
 * 接入豆包时：在 submit() 里发起豆包录音文件识别(异步)，把任务态记进 tasks；
 * 在轮询/回调里把 result 写进 results；status()/result() 改成读真实状态与产物。
 * 接入后删掉本桩或换成正式实现（保留 AsrService 接口不变）。
 */
@Service
@ConditionalOnProperty(prefix = "doubao.asr", name = "enabled", havingValue = "false", matchIfMissing = true)
public class AsrServiceStub implements AsrService {

    // taskId -> 任务态
    private final ConcurrentHashMap<String, AsrTaskVO> tasks = new ConcurrentHashMap<>();
    // meetingId -> 转写结果
    private final ConcurrentHashMap<Long, AsrResult> results = new ConcurrentHashMap<>();

    @Override
    public AsrTaskVO submit(Long meetingId, String audioRef) {
        return submit(meetingId, null, null);
    }

    @Override
    public AsrTaskVO submit(Long meetingId, Long recordingId, CommitteeService committeeService) {
        // 桩实现：无论 recordingId 是否为空，都直接返回模拟转写结果
        String taskId = "asr_" + UUID.randomUUID().toString().substring(0, 8);

        // TODO 接入豆包：此处调用豆包【录音文件识别】异步接口，传 audioRef(对象存储地址)，
        //  开启说话人分离 + 传入热词表(委员姓名/小区术语)，拿到豆包任务ID并落库。
        //  真实实现下，结果由回调或轮询写入；桩里直接造一段假转写并标记 done。
        results.put(meetingId, mockTranscript(meetingId));

        AsrTaskVO task = AsrTaskVO.builder()
                .taskId(taskId).meetingId(meetingId).status("done").build();
        tasks.put(taskId, task);
        return task;
    }

    @Override
    public AsrTaskVO status(String taskId) {
        AsrTaskVO t = tasks.get(taskId);
        if (t == null) {
            return AsrTaskVO.builder().taskId(taskId).status("failed").message("任务不存在").build();
        }
        return t;
    }

    @Override
    public AsrResult result(Long meetingId) {
        return results.get(meetingId);
    }

    // —— 仅桩用：演示转写 ——
    private AsrResult mockTranscript(Long meetingId) {
        return AsrResult.builder()
                .meetingId(meetingId)
                .durationSec(1800)
                .segments(List.of(
                        seg("S1", 0, 8000, "我们先讨论物业费调整这个议题。"),
                        seg("S2", 8200, 12000, "我同意按方案上调。"),
                        seg("S3", 12500, 16000, "我也同意。"),
                        seg("S1", 16500, 22000, "另外有人临时提出增设非机动车停车点，大家看一下。"),
                        seg("S2", 22500, 25000, "这个可以再议。")
                ))
                .build();
    }

    private AsrResult.Segment seg(String spk, long s, long e, String text) {
        return AsrResult.Segment.builder().speaker(spk).startMs(s).endMs(e).text(text).build();
    }
}
