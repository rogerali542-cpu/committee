package com.ywh.dto;

import lombok.Data;
import java.util.List;

@Data
public class ProxyActionRequest {
    private String actionType; // signIn, vote
    private List<Long> memberIds;
    private Long topicId;
    private String choice;
    private Long selectedId;
    private String proofUrl;
    private String proofName;
}
