package com.music.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密器配置。
 *
 * <p>仅借用 Spring Security 提供的 {@link BCryptPasswordEncoder} 做密码哈希，
 * 不使用 Security 的任何鉴权能力。bcrypt 自带随机盐并内嵌进密文，
 * 校验时用 {@code matches(原文, 密文)} 即可（盐从密文中自动取出）。</p>
 */
@Configuration
public class PasswordConfig {

    /**
     * 注册全局密码加密器 Bean。
     *
     * @return BCrypt 密码加密器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
