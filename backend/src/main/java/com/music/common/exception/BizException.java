package com.music.common.exception;

import com.music.common.result.ResultCode;

/**
 * 业务异常。
 *
 * <p>用于在 Service 层主动中断流程并向上抛出可预期的业务错误
 * （如「用户名已存在」「旧密码不正确」「账号已被封禁」）。
 * 由 {@link GlobalExceptionHandler} 统一捕获并转为标准失败响应。</p>
 *
 * <p>与系统异常的区别：业务异常是预期内的、需要给用户明确提示的；
 * 系统异常（空指针、SQL 错误等）是非预期的，统一兜底为 500。</p>
 */
public class BizException extends RuntimeException {

    /** 关联的响应状态码，决定最终返回给前端的 code。 */
    private final ResultCode resultCode;

    /**
     * 用默认 {@link ResultCode#BIZ_ERROR} 构造，仅自定义提示信息。
     *
     * @param message 给用户的错误提示
     */
    public BizException(String message) {
        super(message);
        this.resultCode = ResultCode.BIZ_ERROR;
    }

    /**
     * 指定状态码与提示信息构造。
     *
     * @param resultCode 响应状态码
     * @param message    给用户的错误提示
     */
    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

    /** @return 关联的响应状态码 */
    public ResultCode getResultCode() {
        return resultCode;
    }
}
