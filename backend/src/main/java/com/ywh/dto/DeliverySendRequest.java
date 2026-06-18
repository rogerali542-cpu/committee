package com.ywh.dto;

import lombok.Data;

import java.util.List;

@Data
public class DeliverySendRequest {
    private List<Long> memberIds;
}
