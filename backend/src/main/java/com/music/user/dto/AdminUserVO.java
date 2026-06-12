package com.music.user.dto;

import com.music.user.entity.User;

import java.time.OffsetDateTime;

/**
 * 管理后台的用户视图数据。
 *
 * <p>是 {@link User} 实体的安全投影——<b>剔除 password</b>，但相比面向用户自己的
 * {@link UserInfoVO} <b>额外暴露 {@code status}（封禁状态）</b>，
 * 因为管理员需要据此识别并管理被封禁账号。</p>
 */
public class AdminUserVO {

    /** 用户 uid。 */
    private Long uid;

    /** 登录名。 */
    private String username;

    /** 昵称。 */
    private String nickname;

    /** 邮箱。 */
    private String email;

    /** 头像路径。 */
    private String avatar;

    /** 角色：0 普通 / 1 上传者 / 2 管理员。 */
    private Integer role;

    /** 账号状态：0 正常 / 1 封禁。 */
    private Integer status;

    /** 注册时间。 */
    private OffsetDateTime regTime;

    /**
     * 由用户实体构建管理视图 VO，过滤密码、保留状态。
     *
     * @param user 用户实体
     * @return 管理后台用户 VO
     */
    public static AdminUserVO from(User user) {
        AdminUserVO vo = new AdminUserVO();
        vo.uid = user.getUid();
        vo.username = user.getUsername();
        vo.nickname = user.getNickname();
        vo.email = user.getEmail();
        vo.avatar = user.getAvatar();
        vo.role = user.getRole();
        vo.status = user.getStatus();
        vo.regTime = user.getRegTime();
        return vo;
    }

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public Integer getRole() {
        return role;
    }

    public void setRole(Integer role) {
        this.role = role;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public OffsetDateTime getRegTime() {
        return regTime;
    }

    public void setRegTime(OffsetDateTime regTime) {
        this.regTime = regTime;
    }
}
