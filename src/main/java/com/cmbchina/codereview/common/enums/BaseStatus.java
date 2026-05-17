package com.cmbchina.codereview.common.enums;

public enum BaseStatus {

    DISABLED(0),
    ENABLED(1);

    private final int value;

    BaseStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
