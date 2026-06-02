package com.music.user.service;

import com.music.user.dto.LoginDTO;
import com.music.user.dto.LoginVO;
import com.music.user.dto.RegisterDTO;
import com.music.user.dto.UpdatePasswordDTO;
import com.music.user.dto.UpdateProfileDTO;
import com.music.user.entity.User;

/**
 * 用户与鉴权业务接口。
 *
 * <p>涵盖注册、登录、登出、资料查询与修改、密码修改。
 * 鉴权采用 Session + Redis：登录写会话、登出删会话。</p>
 */
public interface UserService {

    /**
     * 用户注册。
     *
     * <p>校验用户名/邮箱唯一性，密码 bcrypt 加密后落库，默认角色为普通用户。</p>
     *
     * @param dto 注册参数
     * @return 新用户的 uid
     * @throws com.music.common.exception.BizException 用户名或邮箱已存在
     */
    Long register(RegisterDTO dto);

    /**
     * 用户登录。
     *
     * <p>校验密码、账号状态（未封禁且未软删除），通过后创建 Redis 会话并返回令牌。</p>
     *
     * @param dto 登录参数
     * @return 含令牌与用户信息的登录响应
     * @throws com.music.common.exception.BizException 用户名或密码错误、账号被封禁
     */
    LoginVO login(LoginDTO dto);

    /**
     * 用户登出：删除其 Redis 会话，使令牌即时失效。
     *
     * @param token 当前会话令牌（sessionId）
     */
    void logout(String token);

    /**
     * 查询指定用户实体（供获取个人资料用）。
     *
     * @param uid 用户 uid
     * @return 用户实体
     * @throws com.music.common.exception.BizException 用户不存在
     */
    User getByUid(Long uid);

    /**
     * 修改个人资料（昵称、头像）。
     *
     * @param uid 当前用户 uid
     * @param dto 资料参数
     */
    void updateProfile(Long uid, UpdateProfileDTO dto);

    /**
     * 修改密码：先校验旧密码，再更新为新密码的 bcrypt 密文。
     *
     * @param uid 当前用户 uid
     * @param dto 改密参数
     * @throws com.music.common.exception.BizException 旧密码不正确
     */
    void updatePassword(Long uid, UpdatePasswordDTO dto);
}
