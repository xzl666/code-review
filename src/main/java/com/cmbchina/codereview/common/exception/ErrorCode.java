package com.cmbchina.codereview.common.exception;

public enum ErrorCode {

    SUCCESS("0", "success"),
    PARAM_ERROR("400", "请求参数错误"),
    BIZ_ERROR("1000", "业务处理失败"),
    NOT_FOUND("1001", "资源不存在"),
    SYSTEM_ERROR("500", "系统异常");

    private final String code;

    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
