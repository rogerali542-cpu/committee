package com.ywh.service.quick;

import com.ywh.dto.quick.AsrResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 直通实现：doubao.llm.enabled=false（或未配）时启用，不做任何纠错。
 */
@Service
@ConditionalOnProperty(prefix = "doubao.llm", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopTranscriptCorrectionService implements TranscriptCorrectionService {

    @Override
    public AsrResult correct(Long meetingId, AsrResult asr) {
        return asr;
    }
}
