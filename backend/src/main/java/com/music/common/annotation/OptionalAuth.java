package com.music.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 可选鉴权注解（软鉴权）。
 *
 * <p>标注在 Controller 方法（或整个类）上，声明「该接口<strong>允许游客访问</strong>，
 * 但若访问者已登录，则应识别其身份」。典型用于「公开浏览 + 登录后展示个性化信息」
 * 的接口，例如评论列表（游客可看，登录用户额外回填"我是否点过赞"）。</p>
 *
 * <p>拦截器对标注本注解的接口按 token 是否存在区分处理：</p>
 * <ul>
 *   <li><b>未携带 {@code X-Token}</b> —— 视为游客，直接放行，不绑定用户态
 *       （{@link com.music.common.context.UserContext} 保持为空，
 *       下游 {@code getUid()} 返回 null）；</li>
 *   <li><b>携带了 {@code X-Token} 但无效/已失效</b> —— 说明用户曾登录、会话已过期，
 *       抛 401 提示重新登录，<strong>不</strong>静默降级为游客（避免用户困惑于
 *       "我的点赞高亮怎么没了"）；</li>
 *   <li><b>携带了有效 {@code X-Token}</b> —— 绑定用户态并刷新会话 TTL，
 *       与正常登录访问一致。</li>
 * </ul>
 *
 * <p>与 {@link RequireRole} 的区别：{@code RequireRole} 是<strong>强鉴权</strong>
 * （必须登录，否则 401，还可限定角色）；本注解是<strong>软鉴权</strong>
 * （可不登录）。两者语义互斥，不应同时标注在同一接口上。软鉴权下不做角色校验。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OptionalAuth {
}
