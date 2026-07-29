package com.ywh.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ywh.enums.ComplianceStatus;
import com.ywh.enums.MeetingMode;
import com.ywh.enums.MeetingMethod;
import com.ywh.enums.MeetingStage;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class MeetingDetailVO {
    private Long id;
    private String title;
    private LocalDate meetingDate;
    private LocalTime meetingTime;
    private String location;
    private Double locationLat;  // 地图选点经纬度（0723，可空）：前端导航用精确坐标
    private Double locationLng;
    private MeetingMethod meetingMethod;
    private String description;
    private MeetingStage stage;
    private ComplianceStatus compliance;
    private MeetingMode meetingMode;   // normal / quick
    private List<String> invalidNotes;

    // 直接归档标记（前端历史字段名 _archived，helpers/详情页/纪要页均按此读取）
    @JsonProperty("_archived")
    private Boolean archivedFlag;

    // Current user's task summary
    private String taskLevel;   // todo, warn, ok, readonly
    private String taskTitle;
    private List<String> taskItems;
    private String taskHint;
    private String flowNodeText;

    // Identity info
    private String userRole;
    private String userView;    // chair, member, recorder, owner, property

    // 通知后锁定（规则8）：coreLocked=true 时重大字段（日期/时间/地点/参会范围）应锁定，修改需重新通知
    private Boolean coreLocked;
    private String notifiedAt;
    private String notifiedByName;   // 发送通知的操作人"姓名·角色"（谁发的通知）
    private List<NotificationLogVO> notificationLogs;  // 全部通知记录（按时间升序）

    @Data
    public static class NotificationLogVO {
        private String sentAt;
        private String sentByName;
        private String channel;
    }

    // Delivery info
    private DeliveryInfoVO delivery;
    private NoticeDraftVO noticeDraft;
    private List<Map<String, Object>> materials;
    private List<Map<String, Object>> archiveExtras;

    // 当前用户（委员）自己的送达/已读状态（准备阶段）
    private MyDeliveryVO myDelivery;

    // Record info
    private RecordInfoVO record;

    // 会议纪要是否已生成（record.minutesText 非空）→ 详情页据此显示「生成会议纪要」还是「查看会议纪要」
    private Boolean minutesReady;

    // Publish info (ended stage)
    private PublishInfoVO publish;

    // Scores (ended stage)
    private PublishScoreVO publishScore;

    /** 业委会全称（含届别）：落款/抬头统一用，如「阳光花园业主委员会（第一届）」 */
    private String orgFullName;
    /** 列席人员（居委/街道/物业等非委员到会者，顿号分隔） */
    private String observers;

    // Members
    private List<MemberSummaryVO> members;

    @Data
    public static class NoticeDraftVO {
        private String title;
        private String content;
        private String status;
    }

    @Data
    public static class DeliveryInfoVO {
        private String deadlineStr;
        private Integer daysLeft;
        private Integer noticeDone;       // 主任已送达通知的人数
        private Integer materialDone;     // 主任已送达材料的人数
        private Integer readDone;         // 委员已读通知的人数
        private Integer total;
        private Boolean allDone;
        private Boolean noDate;
        private List<MemberDeliveryVO> memberDeliveries;

        @Data
        public static class MemberDeliveryVO {
            private Long userRoleId;
            private String name;
            private String role;
            private Boolean noticeDelivered;
            private Boolean materialDelivered;
            private Boolean noticeRead;       // 该委员是否已读通知
        }
    }

    @Data
    public static class MyDeliveryVO {
        private Boolean noticeDelivered;
        private Boolean materialDelivered;
        private Boolean noticeRead;
        private Boolean materialRead;
    }

    @Data
    public static class RecordInfoVO {
        private Boolean hasDecision;
        private Boolean hasMajorIssue;
        private String juweiName;
        private Boolean juweiSigned;
        private String recordingUrl;   // 已转写的最新录音链接（会后回放/下载，DEPRECATED 后改用 recordings）
        private List<RecordingVO> recordings; // 多条录音，每条记录上传人/时间/ASR 状态
        private List<AttendanceVO> attendances;
        private List<TopicVO> topics;
        private List<EvidenceVO> evidences;
        private List<CheckVO> checks;
        private String recordLevel;    // complete, minor, incomplete
        private String recordText;

        @Data
        public static class AttendanceVO {
            private Long userRoleId;
            private String name;
            private String role;
            private String roomNumber;
            private Boolean signedIn;
            private Boolean signed;
            private Boolean declined;
            private String attendanceMode;
            private Boolean proxySignAuthorized;
            private String proxySignAuthorizedAt;
            private Boolean isSelf;
            private Boolean isProxy;
            private String operatorName;
            private String proofUrl;
        }

        @Data
        public static class TopicVO {
            private Long id;
            private String title;
            private String type;
            private Boolean voteRequired;
            private Integer forVotes;
            private Integer agVotes;
            private Integer abVotes;
            private Integer voted;         // 已表决人数（含 multi_choice 的 selectedId 票）
            private Integer opinionCount;  // 议题意见数（角标用）
            private Integer total;
            private Integer need;
            private Boolean passed;
            private Boolean voteClosed;   // 表决是否已结束揭晓（主任点「结束表决」后为 true）
            private String status;     // passed, pending, failed
            private String text;
            private String summaryDraft; // 主持人确认后的议题结果摘要（线上结果卡/会后材料复用）
            private String decisionType;
            // 通报类：正文 + 已通报状态 + 本人是否看过 + 已读进度（已确认「我已读」人数 / 已签到人数）
            private String content;
            private Boolean notified;
            private Boolean viewedByMe;
            private Integer viewedCount;    // 已确认「我已读」的参会名单委员数
            private Integer signedInCount;  // 兼容字段名：现表示参会名单人数（"全体已通报"的分母）
            private List<Map<String, Object>> options;
            private String myVote;
            private Long mySelectedId;
            private Map<String, Object> myVoteLabel;
            // 留痕（规则6）
            private String source;        // live=现场新增
            private String createdByName;
            private String createdAt;
            // 实名表决（规则5）
            private Boolean realNameVote;
            private List<Map<String, Object>> voterChoices;  // 仅实名表决时填充：{name, choice/label}
            // 议题结果人工改动（0729）：主任/秘书改结果后，confirmedResult=改后的值，resultAuditText=最近一次留痕文案
            private String confirmedResult;
            private String resultAuditText;
        }

        @Data
        public static class EvidenceVO {
            private Long id;
            private String fileName;
            private String fileType;
            private String fileUrl;
        }

        @Data
        public static class CheckVO {
            private String label;
            private String detail;
            private Boolean ok;
        }
    }

    @Data
    public static class PublishInfoVO {
        private Boolean published;
        private String publishDate;
        private String publicTitle;
        private String publicContent;
        private String deadlineStr;
        private Integer daysLeft;
        private String scoreState;   // ontime, late, overdue, pending
        // 公示状态机（见 产品边界定稿.md §5）
        private String status;       // pending, published, withdrawn
        private String publishedBy;
        private String publishedAt;
        private Boolean withdrawn;
        private String withdrawnBy;
        private String withdrawnAt;
        private String withdrawReason;
    }

    @Data
    @Builder
    public static class PublishScoreVO {
        private Integer total;
        private Integer ontime;
        private Integer overdue;
        private Integer pending;
    }

    @Data
    public static class MemberSummaryVO {
        private Long userRoleId;
        private String name;
        private String role;
        private String roomNumber;
    }

    @Data
    public static class MinutesRevisionVO {
        private Integer versionNo;
        private String editorName;
        private String createdAt;
        private String content;
    }
}
