package com.ywh.enums;

public enum VoteChoice {
    for_vote("赞成"),
    against("反对"),
    abstain("弃权");

    private final String label;
    VoteChoice(String label) { this.label = label; }
    public String getLabel() { return label; }
}
