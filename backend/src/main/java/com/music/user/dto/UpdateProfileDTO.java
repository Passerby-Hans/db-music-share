package com.music.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改个人资料请求参数（昵称、头像）。
 *
 * <p>不含密码与角色——密码改动走独立接口，角色变更属管理员操作。</p>
 */
public class UpdateProfileDTO {

    /** 新昵称：必填，1~50 字符。 */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称长度不能超过 50 个字符")
    private String nickname;

    /** 新头像路径：可空。 */
    @Size(max = 255, message = "头像路径过长")
    private String avatar;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }
}
