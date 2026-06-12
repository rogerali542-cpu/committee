package com.ywh.dto;

import com.ywh.enums.ComplianceStatus;
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
    private String description;
    private MeetingStage stage;
    private ComplianceStatus compliance;
    private List<String> invalidNotes;

    // Current user's task summary
    private String taskLevel;   // todo, warn, ok, readonly
    private String taskTitle;
    private List<String> taskItems;
    private String taskHint;
    private String flowNodeText;

    // Identity info
    private String userRole;
    private String userView;    // chair, member, recorder, owner, property

    // Delivery info (preparing stage)
    private DeliveryInfoVO delivery;

    // Record info (ongoing stage)
    private RecordInfoVO record;

    // Publish info (ended stage)
    private PublishInfoVO publish;

    // Scores (ended stage)
    private PublishScoreVO publishScore;

    // Members
    private List<MemberSummaryVO> members;

    @Data
    public static class DeliveryInfoVO {
        private String deadlineStr;
        private Integer daysLeft;
        private Integer noticeDone;
        private Integer materialDone;
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
        }
    }

    @Data
    public static class RecordInfoVO {
        private Boolean hasDecision;
        private Boolean hasMajorIssue;
        private String juweiName;
        private Boolean juweiSigned;
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
            private Integer forVotes;
            private Integer agVotes;
            private Integer abVotes;
            private Integer total;
            private Integer need;
            private Boolean passed;
            private String status;     // passed, pending, failed
            private String text;
            private String decisionType;
            private List<Map<String, Object>> options;
            private String myVote;
            private Long mySelectedId;
            private Map<String, Object> myVoteLabel;
        }

        @Data
        public static class EvidenceVO {
            private Long id;
            private String fileName;
            private String fileType;
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
        private String deadlineStr;
        private Integer daysLeft;
        private String scoreState;   // ontime, late, overdue, pending
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
    }
}
