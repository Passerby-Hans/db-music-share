package com.music.user.dto;

import com.music.user.entity.User;

import java.time.OffsetDateTime;

/**
 * 用户资料响应数据（个人中心展示）。
 *
 * <p>是 {@link User} 实体的安全投影——<b>剔除 password 等敏感字段</b>，
 * 只暴露可对外展示的信息。</p>
 */
public class UserInfoVO {

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

    /** 注册时间。 */
    private OffsetDateTime regTime;

    /**
     * 由用户实体构建 VO，自动过滤敏感字段。
     *
     * @param user 用户实体
     * @return 安全的用户资料 VO
     */
    public static UserInfoVO from(User user) {
        UserInfoVO vo = new UserInfoVO();
        vo.uid = user.getUid();
        vo.username = user.getUsername();
        vo.nickname = user.getNickname();
        vo.email = user.getEmail();
        vo.avatar = user.getAvatar();
        vo.role = user.getRole();
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

    public OffsetDateTime getRegTime() {
        return regTime;
    }

    public void setRegTime(OffsetDateTime regTime) {
        this.regTime = regTime;
    }
}
