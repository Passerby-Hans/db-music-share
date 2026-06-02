package com.music.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 用户实体，对应数据库表 {@code app_user}。
 *
 * <p>（表名取 app_user 是因为 user 是 PostgreSQL 保留字。）
 * 承载三种角色：0 普通用户 / 1 上传者 / 2 管理员。</p>
 */
@TableName("app_user")
public class User {

    /**
     * 用户主键 uid。
     * {@code IdType.AUTO} 对齐数据库 IDENTITY 自增策略。
     */
    @TableId(value = "uid", type = IdType.AUTO)
    private Long uid;

    /** 登录名，唯一。 */
    private String username;

    /** 密码 bcrypt 密文（绝不存明文）。 */
    private String password;

    /** 昵称（展示用）。 */
    private String nickname;

    /** 邮箱，唯一、可空。 */
    private String email;

    /** 头像文件路径，可空。 */
    private String avatar;

    /** 角色：0 普通用户 / 1 上传者 / 2 管理员。 */
    private Integer role;

    /** 账号状态：0 正常 / 1 封禁。 */
    private Integer status;

    /** 软删除标记：true 表示已逻辑删除，前台不可见。 */
    private Boolean isDeleted;

    /** 注册时间。 */
    private OffsetDateTime regTime;

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

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public OffsetDateTime getRegTime() {
        return regTime;
    }

    public void setRegTime(OffsetDateTime regTime) {
        this.regTime = regTime;
    }
}
