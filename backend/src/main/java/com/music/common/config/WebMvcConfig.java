package com.music.common.config;

import com.music.common.interceptor.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册会话拦截器与跨域规则。
 *
 * <p>拦截器对所有 {@code /api/**} 生效，但放行注册/登录等公开接口；
 * 跨域配置允许前端开发服务器（Vue）携带令牌请求后端。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 会话鉴权拦截器。 */
    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    /**
     * 注册会话拦截器及其拦截/放行路径。
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                // 拦截所有 API
                .addPathPatterns("/api/**")
                // 放行完全公开接口：注册、登录、连通性探测、Actuator 健康检查
                // 注意：评论查看接口（某歌主评论/某主评论的回复）不再走白名单，
                // 改为进入拦截器并由 @OptionalAuth 软鉴权处理——游客放行、登录用户
                // 识别身份以回填 likedByMe；带失效 token 则提示重新登录。
                .excludePathPatterns(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/ping",
                        "/api/rank/**",
                        "/actuator/**"
                );
    }

    /**
     * 配置全局跨域（CORS）。
     *
     * <p>开发期允许常见的 Vite 开发端口；令牌走自定义头 {@code X-Token}，
     * 需在 allowedHeaders 中放行。</p>
     *
     * @param registry CORS 注册表
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
