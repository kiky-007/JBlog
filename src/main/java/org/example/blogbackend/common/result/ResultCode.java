package org.example.blogbackend.common.result;

import lombok.Getter;
/*
* 枚举
*/
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数校验失败"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "数据冲突"),
    SYSTEM_ERROR(500, "系统繁忙，请稍后重试");

    private final int code;  //final不可修改性
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}