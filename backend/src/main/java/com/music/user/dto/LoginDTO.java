package com.music.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求参数。
 */
public class LoginDTO {

    /** 登录名：必填。 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码：必填（明文传输，由后端 bcrypt 校验；生产环境应走 HTTPS）。 */
    @NotBlank(message = "密码不能为空")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
