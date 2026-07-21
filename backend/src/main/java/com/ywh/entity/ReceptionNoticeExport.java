package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 接待日公告的「导出留痕」（0717）。
 *
 * 用户定的存档口径：接待安排只存当前一份（ReceptionSystem，编辑即覆盖），
 * 但每导出一次记一笔——用来回答「你证明一下每个月都公示了接待时间」。
 * 不做每月一份独立存档：委员每月得「新建本月通知」太重，而留痕已经够证明。
 *
 * ⚠ time_desc/place/person 是导出那一刻的快照，不是外键也不是引用。
 * 必须快照：ReceptionSystem 下个月就被改了，若这里只存个 id，
 * 回头看 7 月那笔会显示 8 月的时间——留痕就成了假证据。
 */
@Entity
@Table(name = "reception_notice_exports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceptionNoticeExport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    /** 导出人姓名。存名字不存 user_id：这是给人看的留痕，委员换届后 id 关联不上就成空白了 */
    @Column(name = "exported_by", length = 30)
    private String exportedBy;

    @Column(name = "exported_at", nullable = false)
    private LocalDateTime exportedAt;

    // ── 以下三个是导出当时的快照 ──
    @Column(name = "time_desc", length = 100)
    private String timeDesc;

    @Column(length = 200)
    private String place;

    @Column(length = 100)
    private String person;
}
