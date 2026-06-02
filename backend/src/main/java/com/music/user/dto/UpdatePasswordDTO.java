package com.music.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求参数。
 *
 * <p>需提供旧密码以二次确认身份，校验通过后才更新为新密码。</p>
 */
public class UpdatePasswordDTO {

    /** 旧密码：必填，用于校验本人操作。 */
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    /** 新密码：必填，6~50 字符。 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 50, message = "新密码长度需为 6~50 个字符")
    private String newPassword;

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
