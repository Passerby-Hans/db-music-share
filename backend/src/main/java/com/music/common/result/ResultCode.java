package com.music.common.result;

/**
 * 统一响应状态码枚举。
 *
 * <p>前后端约定：所有接口响应都携带一个业务状态码 {@code code}，
 * 前端据此判断成功与否、以及失败原因，而不依赖 HTTP 状态码。</p>
 *
 * <p>码段约定：</p>
 * <ul>
 *   <li>{@code 200} —— 成功</li>
 *   <li>{@code 400~499} —— 客户端错误（参数、未登录、无权限等）</li>
 *   <li>{@code 500} —— 服务端内部错误</li>
 * </ul>
 */
public enum ResultCode {

    /** 操作成功。 */
    SUCCESS(200, "成功"),

    /** 请求参数校验不通过。 */
    BAD_REQUEST(400, "请求参数错误"),

    /** 未登录或会话已失效（前端应跳转登录页）。 */
    UNAUTHORIZED(401, "未登录或登录已过期"),

    /** 已登录但角色权限不足。 */
    FORBIDDEN(403, "无权限访问"),

    /** 资源不存在。 */
    NOT_FOUND(404, "资源不存在"),

    /** 业务校验失败（如用户名已存在、旧密码错误等）。 */
    BIZ_ERROR(409, "业务处理失败"),

    /** 服务端未捕获的内部错误。 */
    INTERNAL_ERROR(500, "服务器内部错误");

    /** 业务状态码。 */
    private final int code;

    /** 默认提示信息。 */
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /** @return 业务状态码 */
    public int getCode() {
        return code;
    }

    /** @return 默认提示信息 */
    public String getMessage() {
        return message;
    }
}
