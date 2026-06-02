package com.music.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求参数。
 *
 * <p>字段校验由 {@code @Valid} 在 Controller 触发，
 * 不合法时由全局异常处理器转为 400 响应。</p>
 */
public class RegisterDTO {

    /** 登录名：必填，4~50 字符。 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 50, message = "用户名长度需为 4~50 个字符")
    private String username;

    /** 密码：必填，6~50 字符（存储前会做 bcrypt 哈希）。 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度需为 6~50 个字符")
    private String password;

    /** 昵称：必填，1~50 字符。 */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称长度不能超过 50 个字符")
    private String nickname;

    /** 邮箱：可空；若填写需为合法邮箱格式。 */
    @Email(message = "邮箱格式不正确")
    private String email;

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

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
