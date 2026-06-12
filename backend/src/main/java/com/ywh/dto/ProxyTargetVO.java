package com.ywh.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

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
}
