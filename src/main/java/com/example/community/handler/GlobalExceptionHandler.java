package com.example.community.handler;

import com.example.community.utils.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理所有异常
     */
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        // 开发阶段打印堆栈
        e.printStackTrace();
        return Result.error(e.getMessage());
    }
}