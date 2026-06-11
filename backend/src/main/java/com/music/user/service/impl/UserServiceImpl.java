package com.music.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.music.common.exception.BizException;
import com.music.common.result.ResultCode;
import com.music.common.session.LoginUser;
import com.music.common.session.SessionService;
import com.music.user.dto.LoginDTO;
import com.music.user.dto.LoginVO;
import com.music.user.dto.RegisterDTO;
import com.music.user.dto.UpdatePasswordDTO;
import com.music.user.dto.UpdateProfileDTO;
import com.music.user.entity.User;
import com.music.user.mapper.UserMapper;
import com.music.user.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户与鉴权业务实现。
 *
 * <p>密码用 {@link PasswordEncoder}（BCrypt）哈希与校验；
 * 登录态用 {@link SessionService} 存取于 Redis。</p>
 */
@Service
public class UserServiceImpl implements UserService {

    /** 账号状态：正常。 */
    private static final int STATUS_NORMAL = 0;

    /** 角色：普通用户（注册默认）。 */
    private static final int ROLE_NORMAL = 0;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;

    /**
     * 构造器注入依赖。
     *
     * @param userMapper      用户数据访问
     * @param passwordEncoder BCrypt 密码加密器
     * @param sessionService  Redis 会话服务
     */
    public UserServiceImpl(UserMapper userMapper,
                           PasswordEncoder passwordEncoder,
                           SessionService sessionService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
    }

    /**
     * 用户注册。校验唯一性后，密码哈希落库，默认普通用户角色。
     */
    @Override
    public Long register(RegisterDTO dto) {
        // 用户名唯一性校验
        Long sameName = userMapper.selectCount(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, dto.getUsername()));
        if (sameName != null && sameName > 0) {
            throw new BizException("用户名已存在");
        }
        // 邮箱唯一性校验（仅当填写了邮箱）
        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            Long sameEmail = userMapper.selectCount(
                    Wrappers.<User>lambdaQuery().eq(User::getEmail, dto.getEmail()));
            if (sameEmail != null && sameEmail > 0) {
                throw new BizException("邮箱已被注册");
            }
        }
        // 组装实体：密码 bcrypt 加密，默认角色/状态/未删除
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setRole(ROLE_NORMAL);
        user.setStatus(STATUS_NORMAL);
        user.setIsDeleted(false);
        userMapper.insert(user);
        return user.getUid();
    }

    /**
     * 用户登录。校验密码与账号状态，通过后创建 Redis 会话。
     */
    @Override
    public LoginVO login(LoginDTO dto) {
        // 按用户名查用户（含已软删除者，以便给出准确提示）
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, dto.getUsername()));
        // 用户名不存在 或 密码不匹配：统一提示，避免暴露「用户名是否存在」
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        // 账号状态校验：软删除或封禁均拒绝登录（决策④：不让问题账号拿到令牌）
        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new BizException(ResultCode.FORBIDDEN, "账号不存在或已注销");
        }
        if (user.getStatus() != null && user.getStatus() != STATUS_NORMAL) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已被封禁，请联系管理员");
        }
        // 创建会话：登录态写入 Redis，拿到令牌
        LoginUser loginUser = new LoginUser(
                user.getUid(), user.getUsername(), user.getNickname(), user.getRole());
        String token = sessionService.createSession(loginUser);
        return new LoginVO(token, user.getUid(), user.getUsername(),
                user.getNickname(), user.getRole());
    }

    /**
     * 登出：删除 Redis 会话，令牌即时失效。
     */
    @Override
    public void logout(String token) {
        sessionService.deleteSession(token);
    }

    /**
     * 按 uid 查询用户，不存在则抛业务异常。
     */
    @Override
    public User getByUid(Long uid) {
        User user = userMapper.selectById(uid);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    /**
     * 修改个人资料（昵称、头像）。
     */
    @Override
    public void updateProfile(Long uid, UpdateProfileDTO dto) {
        User user = getByUid(uid);
        user.setNickname(dto.getNickname());
        user.setAvatar(dto.getAvatar());
        userMapper.updateById(user);
    }

    /**
     * 修改密码：校验旧密码后更新为新密码的 bcrypt 密文。
     *
     * <p>改密成功后<b>作废该用户全部会话</b>（含本次操作所用的当前会话）：
     * 密码变更意味着旧凭据应全部失效，所有已登录设备需重新登录，
     * 防止密码可能已泄露的旧会话继续被使用。前端在改密后应引导重新登录。</p>
     */
    @Override
    public void updatePassword(Long uid, UpdatePasswordDTO dto) {
        User user = getByUid(uid);
        // 校验旧密码，防止他人冒用会话改密
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BizException("旧密码不正确");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(user);
        // 改密后作废该用户所有会话，旧令牌即时失效（须重新登录）
        sessionService.deleteSessionsByUid(uid);
    }
}
