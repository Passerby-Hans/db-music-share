package com.music.common.interceptor;

import com.music.common.annotation.OptionalAuth;
import com.music.common.annotation.RequireRole;
import com.music.common.context.UserContext;
import com.music.common.exception.BizException;
import com.music.common.result.ResultCode;
import com.music.common.session.LoginUser;
import com.music.common.session.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 会话鉴权拦截器。
 *
 * <p>对受保护接口执行两段校验：</p>
 * <ol>
 *   <li><b>身份校验</b>：从请求头 {@code X-Token} 取 sessionId，查 Redis；
 *       无效则抛 401，并把有效登录态放入 {@link UserContext}、刷新会话 TTL。</li>
 *   <li><b>角色校验</b>：若目标方法/类标注了 {@link RequireRole}，
 *       比对当前角色是否命中允许集合，不足则抛 403。</li>
 * </ol>
 *
 * <p><b>软鉴权例外</b>：标注 {@link OptionalAuth} 的接口允许游客访问——
 * 未带 token 直接放行（不绑定用户态）；带有效 token 则照常绑定身份+续期；
 * 带失效 token 仍抛 401 提示重新登录。软鉴权下不做角色校验。
 * 用于「公开浏览 + 登录显示个性化信息」类接口（如评论列表的 likedByMe）。</p>
 *
 * <p>完全公开的接口（注册/登录/探活）在 WebMvcConfig 注册时通过
 * {@code excludePathPatterns} 排除，根本不进入本拦截器。</p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** 约定的会话令牌请求头名称（值即 sessionId，无前缀）。 */
    public static final String TOKEN_HEADER = "X-Token";

    /** 会话服务，用于查 Redis 取登录态、刷新 TTL。 */
    private final SessionService sessionService;

    public AuthInterceptor(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * 请求进入 Controller 前执行鉴权。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @param handler  目标处理器（可能是 HandlerMethod，也可能是静态资源处理器）
     * @return {@code true} 放行；抛异常则由全局异常处理器转为标准失败响应
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 非控制器方法（如静态资源、错误转发）直接放行，避免对其做鉴权
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String sessionId = request.getHeader(TOKEN_HEADER);

        // —— 软鉴权分支：标注 @OptionalAuth 的接口允许游客访问 ——
        // 方法级注解优先，方法上没有再看类级
        if (hasAnnotation(handlerMethod, OptionalAuth.class)) {
            // 完全没带 token = 真游客（从未登录）：放行，不绑定用户态
            if (!StringUtils.hasText(sessionId)) {
                return true;
            }
            // 带了 token：尝试解析。有效则绑定身份+续期；
            // 失效/伪造则抛 401——用户曾登录、会话已过期，应提示重新登录，
            // 而非静默降级为游客（否则其个性化信息如点赞高亮会莫名消失）
            LoginUser optionalUser = sessionService.getSession(sessionId);
            if (optionalUser == null) {
                throw new BizException(ResultCode.UNAUTHORIZED, "登录已过期，请重新登录");
            }
            UserContext.set(optionalUser);
            sessionService.refreshSession(sessionId);
            // 软鉴权不做角色校验（允许游客，谈不上角色门槛）
            return true;
        }

        // —— 第 1 段：身份校验（强鉴权，默认）——
        LoginUser loginUser = sessionService.getSession(sessionId);
        if (loginUser == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录或登录已过期，请重新登录");
        }
        // 绑定到当前请求线程，供后续 Service/Controller 取用
        UserContext.set(loginUser);
        // 活跃续期：每次有效访问把会话 TTL 重置为 24h
        sessionService.refreshSession(sessionId);

        // —— 第 2 段：角色校验（仅当目标标注了 @RequireRole）——
        // 方法级注解优先；方法上没有则看类级注解
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole != null) {
            int currentRole = loginUser.getRole();
            boolean allowed = Arrays.stream(requireRole.value()).anyMatch(r -> r == currentRole);
            if (!allowed) {
                throw new BizException(ResultCode.FORBIDDEN, "无权限访问，需要更高角色权限");
            }
        }
        return true;
    }

    /**
     * 判断处理器方法或其所在类是否标注了指定注解（方法级优先于类级）。
     *
     * @param handlerMethod  目标处理器方法
     * @param annotationType 注解类型
     * @return 方法或类上存在该注解返回 true
     */
    private boolean hasAnnotation(HandlerMethod handlerMethod,
                                  Class<? extends java.lang.annotation.Annotation> annotationType) {
        return handlerMethod.getMethodAnnotation(annotationType) != null
                || handlerMethod.getBeanType().isAnnotationPresent(annotationType);
    }

    /**
     * 请求完成后清理 ThreadLocal，防止线程池复用导致用户态串号。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @param handler  目标处理器
     * @param ex       处理过程中抛出的异常（若有）
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
