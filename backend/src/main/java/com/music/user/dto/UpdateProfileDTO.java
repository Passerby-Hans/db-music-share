package com.music.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改个人资料请求参数（仅昵称）。
 *
 * <p><b>不含头像</b>：头像变更走专用接口 {@code POST /api/user/avatar}
 * （上传图片、服务端生成 key），不允许客户端直接提交 avatar 字符串，
 * 以杜绝把 avatar 设为任意 object key 进而触发越权删文件。
 * 密码改动走独立接口，角色变更属管理员操作。</p>
 */
public class UpdateProfileDTO {

    /** 新昵称：必填，1~50 字符。 */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称长度不能超过 50 个字符")
    private String nickname;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
