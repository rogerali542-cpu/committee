package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 接待日安排（每个社区一行，编辑即覆盖）。
 * 0717 重新启用：方案A 删掉 Reception.vue 后这张表变成了没人调的孤儿，
 * 现在接回「编辑 + 导出打印接待日公告」。导出留痕见 ReceptionNoticeExport。
 */
@Entity
@Table(name = "reception_systems")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceptionSystem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @Column(nullable = false)
    private Boolean published;

    @Column(name = "time_desc", length = 100)
    private String timeDesc;

    @Column(length = 200)
    private String place;

    @Column(length = 100)
    private String person;

    /**
     * 调整原因（选填，0717）。公告改说话口吻后句式随它变：
     * 有原因 →「因{原因}，需要调整近期的业主接待时间…」；空 → 不带原因的平铺通告句。
     * 只存最近一次的，跟本表「编辑即覆盖」口径一致。
     */
    @Column(name = "adjust_reason", length = 200)
    private String adjustReason;

    /**
     * 上次修改时间。「按规定每月要设接待时间、主任时间不定所以每月要改」——
     * 那么「这个月改了没」就是委员每月的第一个问题，没这个字段答不上来。
     * 可空：老数据（本字段之前建的行）没有，前端按「未记录」处理。
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 「接待时间调整通知」上下文（0731 用户定：公告改对比框样式）。存本次调整的「原安排」
     * 时间/地点 + 生效日期，供 PDF 渲染「原安排 vs 现调整为」对比框、与前端预览逐字一致。
     * 三者均可空（ddl-auto:update 启动时自动加列；仅换人/首次设置时为空 → 退回平铺公告）。
     */
    @Column(name = "prev_time_desc", length = 100)
    private String prevTimeDesc;

    @Column(name = "prev_place", length = 200)
    private String prevPlace;

    /** 生效日期，已格式化成中文串（如「2026年8月6日」）直接印到纸上，重印不漂移。 */
    @Column(name = "effective_date", length = 40)
    private String effectiveDate;
}
