package com.xiaoli.yunpicturebackend.exception;

import com.xiaoli.yunpicturebackend.common.BaseResponse;
import com.xiaoli.yunpicturebackend.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
//全局异常处理类
@RestControllerAdvice //全局异常处理
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException",e);
        return ResultUtils.error(e.getCode(),e.getMessage());
    }
    @ExceptionHandler(Exception.class)
    public BaseResponse<?> exceptionHandler(RuntimeException e) {
        log.error("RuntimeException",e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR,"系统错误");
    }
}
