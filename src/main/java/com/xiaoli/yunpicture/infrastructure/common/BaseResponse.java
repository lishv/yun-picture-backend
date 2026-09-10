package com.xiaoli.yunpicture.infrastructure.common;

import com.xiaoli.yunpicture.infrastructure.exception.ErrorCode;
import lombok.Getter;

import java.io.Serializable;

/**
 * 全局响应封装
 * @param <T>
 */
@Getter
public class BaseResponse<T> implements Serializable {
    private int code;
    private String message;
    private T data;

    public BaseResponse(int code,  T data,String message) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    public BaseResponse(int code, T data) {
        this(code,data,"");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(),null,errorCode.getMessage());

    }
}
