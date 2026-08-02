package com.erp.common.exception;

import com.alibaba.excel.exception.ExcelAnalysisException;
import com.erp.common.api.Result;
import com.erp.common.api.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.validation.ConstraintViolationException;

/**
 * 全局异常处理 - 统一返回 {@link Result}
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.failed(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst().map(FieldError::getDefaultMessage)
                .orElse(ResultCode.VALIDATE_FAILED.getMessage());
        return Result.failed(ResultCode.VALIDATE_FAILED.getCode(), msg);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst().map(FieldError::getDefaultMessage)
                .orElse(ResultCode.VALIDATE_FAILED.getMessage());
        return Result.failed(ResultCode.VALIDATE_FAILED.getCode(), msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        return Result.failed(ResultCode.VALIDATE_FAILED.getCode(), e.getMessage());
    }

    @ExceptionHandler(ExcelAnalysisException.class)
    public Result<Void> handleExcelAnalysisException(ExcelAnalysisException e) {
        log.warn("Excel 导入解析失败: {}", e.getMessage());
        return Result.failed(ResultCode.VALIDATE_FAILED.getCode(),
                "Excel 文件无法解析，请使用系统最新模板并检查单元格格式");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        return Result.failed(ResultCode.VALIDATE_FAILED.getCode(), "上传文件过大");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.failed(ResultCode.VALIDATE_FAILED.getCode(),
                "请求参数格式不正确: " + e.getName());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        return Result.failed(ResultCode.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public Result<Void> handleAuthentication(AuthenticationException e) {
        return Result.failed(ResultCode.UNAUTHORIZED.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.failed(ResultCode.FAILED.getCode(), "系统繁忙，请稍后再试");
    }
}
