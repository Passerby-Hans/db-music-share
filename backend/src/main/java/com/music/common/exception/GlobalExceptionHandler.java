package com.music.common.exception;

import com.music.common.result.Result;
import com.music.common.result.ResultCode;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 *
 * <p>通过 {@link RestControllerAdvice} 拦截所有 Controller 抛出的异常，
 * 统一转换为 {@link Result} 失败响应，避免异常堆栈直接暴露给前端，
 * 也免去每个接口重复写 try-catch。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常（预期内错误）。
     *
     * @param e 业务异常
     * @return 携带业务状态码与提示的失败响应
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        return Result.error(e.getResultCode(), e.getMessage());
    }

    /**
     * 处理 {@code @Valid} 参数校验失败（请求体 DTO 字段不合法）。
     *
     * <p>取第一条字段错误信息返回，提示更友好。</p>
     *
     * @param e 参数校验异常
     * @return 400 失败响应，message 为首个字段的校验提示
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ResultCode.BAD_REQUEST.getMessage());
        return Result.error(ResultCode.BAD_REQUEST, msg);
    }

    /**
     * 处理表单绑定校验失败（非请求体场景的兜底）。
     *
     * @param e 绑定异常
     * @return 400 失败响应
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ResultCode.BAD_REQUEST.getMessage());
        return Result.error(ResultCode.BAD_REQUEST, msg);
    }

    /**
     * 兜底处理所有未捕获的系统异常（非预期错误）。
     *
     * <p>统一返回 500，避免泄露内部实现细节；具体堆栈打印到服务端日志。</p>
     *
     * @param e 未捕获异常
     * @return 500 失败响应
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        // 打印堆栈便于排查；生产可替换为日志框架
        e.printStackTrace();
        return Result.error(ResultCode.INTERNAL_ERROR, "服务器内部错误：" + e.getMessage());
    }
}
