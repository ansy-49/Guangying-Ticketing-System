package com.guangying.domain.model.vo;

import com.guangying.domain.enums.ResponseCodeEnum;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应包装类
 */
@Data
public class Result<T> implements Serializable {

    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.setCode(ResponseCodeEnum.SUCCESS.getCode());
        r.setMessage(ResponseCodeEnum.SUCCESS.getMessage());
        r.setData(data);
        return r;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    /** Alias for success — 简洁写法 */
    public static <T> Result<T> ok(T data) {
        return success(data);
    }

    /** Alias for success — 简洁写法 */
    public static <T> Result<T> ok() {
        return success(null);
    }

    public static <T> Result<T> fail(String message) {
        Result<T> r = new Result<>();
        r.setCode(ResponseCodeEnum.INTERNAL_ERROR.getCode());
        r.setMessage(message);
        return r;
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    public static <T> Result<T> fail(ResponseCodeEnum codeEnum) {
        Result<T> r = new Result<>();
        r.setCode(codeEnum.getCode());
        r.setMessage(codeEnum.getMessage());
        return r;
    }
}
