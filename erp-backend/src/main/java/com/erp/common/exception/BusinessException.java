package com.erp.common.exception;

import com.erp.common.api.ResultCode;
import lombok.Getter;

/**
 * 业务异常 - 由 GlobalExceptionHandler 统一捕获并转换为标准响应
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.FAILED.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }
}
