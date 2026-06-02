package com.music.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色鉴权注解。
 *
 * <p>标注在 Controller 方法（或整个类）上，声明「访问该接口所需的最低/指定角色」。
 * 由会话拦截器统一反射读取并校验当前登录用户的角色，集中权限逻辑、便于复用。</p>
 *
 * <p>用法示例：{@code @RequireRole(2)} 表示仅管理员可访问；
 * 可传多个角色 {@code @RequireRole({1, 2})} 表示上传者或管理员均可。</p>
 *
 * <p>未标注本注解的接口：只要登录即可访问（拦截器仍校验会话有效性）。
 * 完全公开的接口（注册/登录）通过拦截器的放行白名单排除，不在此列。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /**
     * 允许访问的角色码集合（0 普通 / 1 上传者 / 2 管理员）。
     * 当前用户角色命中其中任一即放行。
     *
     * @return 允许的角色码数组
     */
    int[] value();
}
