package com.music.common.result;

import java.io.Serializable;

/**
 * 统一响应包装体。
 *
 * <p>所有 Controller 接口统一返回 {@code Result<T>}，结构固定为
 * {@code {code, message, data}}，便于前端统一处理成功/失败逻辑。</p>
 *
 * @param <T> 业务数据的类型
 */
public class Result<T> implements Serializable {

    /** 业务状态码，取值见 {@link ResultCode}。 */
    private int code;

    /** 提示信息（成功或失败原因）。 */
    private String message;

    /** 业务数据载荷；失败时通常为 {@code null}。 */
    private T data;

    /** 全参构造器（私有，统一通过静态工厂方法创建）。 */
    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 构造成功响应（带数据）。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 成功响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 构造成功响应（无数据，用于纯操作类接口如登出）。
     *
     * @param <T> 数据类型
     * @return 成功响应，data 为 null
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 按状态码构造失败响应，使用枚举的默认提示。
     *
     * @param rc  状态码枚举
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> Result<T> error(ResultCode rc) {
        return new Result<>(rc.getCode(), rc.getMessage(), null);
    }

    /**
     * 按状态码构造失败响应，并自定义提示信息。
     *
     * @param rc      状态码枚举
     * @param message 自定义提示（覆盖枚举默认值）
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> Result<T> error(ResultCode rc, String message) {
        return new Result<>(rc.getCode(), message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
