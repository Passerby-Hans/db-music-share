package com.music.user.dto;

/**
 * 登录成功响应数据。
 *
 * <p>返回会话令牌（sessionId）与用户基本信息。前端保存 token，
 * 后续请求放入 {@code X-Token} 请求头。</p>
 */
public class LoginVO {

    /** 会话令牌（sessionId），后续请求凭此鉴权。 */
    private String token;

    /** 用户 uid。 */
    private Long uid;

    /** 登录名。 */
    private String username;

    /** 昵称。 */
    private String nickname;

    /** 角色：0 普通用户 / 1 上传者 / 2 管理员（前端据此控制菜单/路由）。 */
    private Integer role;

    /**
     * 全参构造器。
     *
     * @param token    会话令牌
     * @param uid      用户 uid
     * @param username 登录名
     * @param nickname 昵称
     * @param role     角色码
     */
    public LoginVO(String token, Long uid, String username, String nickname, Integer role) {
        this.token = token;
        this.uid = uid;
        this.username = username;
        this.nickname = nickname;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
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
