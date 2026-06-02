package com.music.user.controller;

import com.music.common.context.UserContext;
import com.music.common.result.Result;
import com.music.user.dto.UpdatePasswordDTO;
import com.music.user.dto.UpdateProfileDTO;
import com.music.user.dto.UserInfoVO;
import com.music.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户个人中心接口：查询资料、修改资料、修改密码。
 *
 * <p>本控制器下所有接口均需登录（不在放行白名单）。当前用户身份
 * 从 {@link UserContext}（由会话拦截器写入）获取，无需前端再传 uid，
 * 防止越权操作他人数据。</p>
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 获取当前登录用户的资料。
     *
     * @return 用户资料 VO（不含密码）
     */
    @GetMapping("/me")
    public Result<UserInfoVO> me() {
        Long uid = UserContext.getUid();
        return Result.success(UserInfoVO.from(userService.getByUid(uid)));
    }

    /**
     * 修改当前用户的昵称/头像。
     *
     * @param dto 资料参数
     * @return 成功响应
     */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@Valid @RequestBody UpdateProfileDTO dto) {
        userService.updateProfile(UserContext.getUid(), dto);
        return Result.success();
    }

    /**
     * 修改当前用户的密码。
     *
     * @param dto 改密参数（含旧密码校验）
     * @return 成功响应
     */
    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody UpdatePasswordDTO dto) {
        userService.updatePassword(UserContext.getUid(), dto);
        return Result.success();
    }
}
