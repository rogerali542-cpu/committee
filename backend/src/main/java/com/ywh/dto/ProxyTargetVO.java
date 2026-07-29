package com.ywh.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ProxyTargetVO {
    private Long memberId;
    private String name;
    private String role;
    private String roomNumber;
    private Boolean signedIn;
    private Boolean signed;
    private Boolean signInByProxy;
    private String signInOperatorName;
    private String proofUrl;
    private List<Long> votedTopicIds;
    // 该委员"由主任代投"的票：topicId → 当前票值（simple=for_vote/against/abstain；multi=选项 id 字符串）。
    // 仅代投的票在此，用于代投面板"改投"（本人自投的不列入、不可代改）。
    private Map<Long, String> proxyVotes;
}
