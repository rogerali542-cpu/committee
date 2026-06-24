package com.ywh.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RecordingVO {
    private Long id;
    private String uploaderName;
    private String recordingUrl;
    private String fileName;
    private Long fileSize;
    private String asrStatus;
    private LocalDateTime createdAt;
}
