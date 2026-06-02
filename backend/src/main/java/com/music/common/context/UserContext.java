package com.music.common.context;

import com.music.common.session.LoginUser;

/**
 * 当前请求的用户上下文（基于 ThreadLocal）。
 *
 * <p>会话拦截器在校验通过后，把当前 {@link LoginUser} 放入本上下文；
 * 后续 Service/Controller 可随时通过 {@link #get()} 取到「当前是谁」，
 * 无需层层透传参数。请求结束时务必调用 {@link #clear()} 清理，
 * 防止线程复用（线程池）导致的用户态串号。</p>
 */
public final class UserContext {

    /** 每个请求线程独立持有的登录态。 */
    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    /** 工具类不允许实例化。 */
    private UserContext() {
    }

    /**
     * 绑定当前请求的登录态。
     *
     * @param loginUser 登录态
     */
    public static void set(LoginUser loginUser) {
        HOLDER.set(loginUser);
    }

    /**
     * 获取当前请求的登录态。
     *
     * @return 登录态；未登录场景下为 {@code null}
     */
    public static LoginUser get() {
        return HOLDER.get();
    }

    /**
     * 便捷方法：获取当前用户 uid。
     *
     * @return 当前用户 uid；未登录时为 {@code null}
     */
    public static Long getUid() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.getUid();
    }

    /**
     * 清理当前线程的登录态。
     *
     * <p>必须在请求结束时调用（拦截器 afterCompletion），否则线程池
     * 复用线程时会读到上一个请求残留的用户态。</p>
     */
    public static void clear() {
        HOLDER.remove();
    }
}
