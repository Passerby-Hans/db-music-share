package com.music.user.controller;

import com.music.common.interceptor.AuthInterceptor;
import com.music.common.result.Result;
import com.music.user.dto.LoginDTO;
import com.music.user.dto.LoginVO;
import com.music.user.dto.RegisterDTO;
import com.music.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鉴权接口：注册、登录、登出。
 *
 * <p>注册与登录为公开接口（在 WebMvcConfig 放行白名单中）；
 * 登出需携带有效令牌（不在白名单，会经过会话拦截器）。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 用户注册。
     *
     * @param dto 注册参数（已 @Valid 校验）
     * @return 新用户 uid
     */
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success(userService.register(dto));
    }

    /**
     * 用户登录。
     *
     * @param dto 登录参数
     * @return 令牌与用户信息
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    /**
     * 用户登出：从请求头取令牌并删除对应会话。
     *
     * @param request 当前请求（用于读取 X-Token 头）
     * @return 成功响应
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = request.getHeader(AuthInterceptor.TOKEN_HEADER);
        userService.logout(token);
        return Result.success();
    }
}
