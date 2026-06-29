package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "meeting_materials")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "size_text")
    private String sizeText;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    /** OCR 文字识别状态：null/none=不适用（非图片/PDF），processing=识别中，done=已识别，failed=识别失败。 */
    @Column(name = "ocr_status")
    private String ocrStatus;

    /** OCR 识别出的全文（PDF 逐页 / 图片直接），供 AI 生成纪要时作为材料摘录引用。可较长，用 MEDIUMTEXT。 */
    @Column(name = "ocr_text", columnDefinition = "MEDIUMTEXT")
    private String ocrText;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { createdAt = LocalDateTime.now(); }
}
