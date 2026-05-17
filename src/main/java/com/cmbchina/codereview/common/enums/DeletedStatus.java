package com.cmbchina.codereview.common.enums;

public enum DeletedStatus {

    NORMAL(0),
    DELETED(1);

    private final int value;

    DeletedStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
