package com.music.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security 放行配置。
 *
 * <p>引入 spring-boot-starter-security 后，其默认行为会对所有请求开启表单登录/
 * Basic 认证拦截，与本项目自定义的会话拦截器冲突。本配置把 Security 的过滤链
 * <b>全部放行</b>，使其退化为「只提供 {@code BCryptPasswordEncoder}、不参与鉴权」，
 * 真正的鉴权完全交给 {@code AuthInterceptor}（Session + Redis）。</p>
 */
@Configuration
public class SecurityConfig {

    /**
     * 配置一条「全部放行」的安全过滤链。
     *
     * @param http Security 的 HTTP 配置入口
     * @return 构建好的过滤链
     * @throws Exception 构建异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 关闭 CSRF：前后端分离 + 自定义令牌，不依赖 Cookie，无需 CSRF 防护
                .csrf(csrf -> csrf.disable())
                // 放行所有请求，鉴权交给自定义会话拦截器
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // 关闭默认表单登录与 HTTP Basic，避免弹出登录框/重定向
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }
}
