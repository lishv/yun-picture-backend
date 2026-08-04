package com.xiaoli.yunpicturebackend.exception;

/**
 * 异常工具类
 */
public class ThrowUtils {
    /**
     * 如果条件为true，抛出异常
     * @param condition 条件
     * @param exception 异常
     */
    public static void throwIf(boolean condition, RuntimeException exception){
        if(condition){
            throw exception;
        }
    }

    /**
     * 如果条件为true，抛出异常
     * @param condition 条件
     * @param errorCode 错误码
     * @param message   异常信息
     */
    public static void throwIf(boolean condition, ErrorCode errorCode,String message){
        throwIf(condition,new BusinessException(errorCode,message));

    }

    /**
     * 如果条件为true，抛出异常
     * @param condition 条件
     * @param errorCode 错误码
     */
    public static void throwIf(boolean condition, ErrorCode errorCode){
        throwIf(condition,new BusinessException(errorCode));

    }
}
