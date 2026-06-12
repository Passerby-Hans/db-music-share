package com.music.user.controller;

import com.music.common.context.UserContext;
import com.music.common.result.Result;
import com.music.common.storage.FileValidator;
import com.music.file.dto.UploadResultVO;
import com.music.user.dto.UpdatePasswordDTO;
import com.music.user.dto.UpdateProfileDTO;
import com.music.user.dto.UserInfoVO;
import com.music.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
     * @return 用户资料 VO（不含密码，头像为可展示 URL）
     */
    @GetMapping("/me")
    public Result<UserInfoVO> me() {
        return Result.success(userService.getMyInfo(UserContext.getUid()));
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

    /**
     * 上传/更换当前用户头像（登录即可，不限角色）。
     *
     * <p>一步式：上传图片即存入公开桶、更新本人 avatar 并删旧头像，
     * 返回新头像直链供前端即时展示。图片校验（类型白名单 + 5MB 上限）
     * 交 {@link FileValidator}，与歌曲封面同口径。</p>
     *
     * @param file multipart 图片文件，表单字段名 {@code file}
     * @return 含新头像 key 与公开直链 url
     */
    @PostMapping("/avatar")
    public Result<UploadResultVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        FileValidator.validate(file, FileValidator.MAX_IMAGE_SIZE, FileValidator.IMAGE_EXT, "头像");
        String url = userService.changeAvatar(UserContext.getUid(), file);
        // key 不外露给前端（前端用 url 即可展示），仅回直链
        return Result.success(new UploadResultVO(null, url));
    }
}
