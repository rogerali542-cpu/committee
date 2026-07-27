package com.ywh.enums;

/**
 * 印章种类（依据《印章管理制度》：本业委会印章三枚，分人保管）。
 */
public enum SealType {
    general_assembly("业主大会章"),
    committee("业委会章"),
    finance("财务专用章");

    private final String label;
    SealType(String label) { this.label = label; }
    public String getLabel() { return label; }
}
