package com.music.common.session;

import java.io.Serializable;

/**
 * 登录态对象。
 *
 * <p>登录成功后，将当前用户的核心身份信息封装为本对象存入 Redis
 * （key 为随机 sessionId）。后续请求由会话拦截器据 sessionId 取回，
 * 用于身份识别与角色鉴权。</p>
 *
 * <p>设计要点：只存鉴权必需的最小字段（uid/role 等），
 * 不存密码等敏感信息，也不存易变的展示字段（昵称等以库为准）。</p>
 */
public class LoginUser implements Serializable {

    /** 用户主键 uid。 */
    private Long uid;

    /** 登录名。 */
    private String username;

    /** 昵称（便于前端直接展示，避免每次回查）。 */
    private String nickname;

    /** 角色：0 普通用户 / 1 上传者 / 2 管理员。 */
    private Integer role;

    /** 无参构造器（Redis 反序列化需要）。 */
    public LoginUser() {
    }

    /**
     * 全参构造器。
     *
     * @param uid      用户主键
     * @param username 登录名
     * @param nickname 昵称
     * @param role     角色码
     */
    public LoginUser(Long uid, String username, String nickname, Integer role) {
        this.uid = uid;
        this.username = username;
        this.nickname = nickname;
        this.role = role;
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

    public Integer getRole() {
        return role;
    }

    public void setRole(Integer role) {
        this.role = role;
    }
}
