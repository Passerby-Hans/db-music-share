package com.music.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.music.common.exception.BizException;
import com.music.common.result.PageVO;
import com.music.common.result.ResultCode;
import com.music.common.session.SessionService;
import com.music.user.dto.AdminUserVO;
import com.music.user.entity.User;
import com.music.user.mapper.UserMapper;
import com.music.user.service.AdminUserService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理后台用户管理业务实现。
 *
 * <p>封禁/改角色后调用 {@link SessionService#deleteSessionsByUid(Long)}
 * 作废目标用户全部会话，实现「即时下线 / 角色即时生效」。</p>
 */
@Service
public class AdminUserServiceImpl implements AdminUserService {

    /** 账号状态：正常。 */
    private static final int STATUS_NORMAL = 0;

    /** 账号状态：封禁。 */
    private static final int STATUS_BANNED = 1;

    private final UserMapper userMapper;
    private final SessionService sessionService;

    /**
     * 构造器注入依赖。
     *
     * @param userMapper     用户数据访问
     * @param sessionService 会话服务（封禁/改角色时作废目标用户会话）
     */
    public AdminUserServiceImpl(UserMapper userMapper, SessionService sessionService) {
        this.userMapper = userMapper;
        this.sessionService = sessionService;
    }

    @Override
    public PageVO<AdminUserVO> listUsers(String keyword, Integer role, Integer status,
                                         long page, long size) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        var wrapper = Wrappers.<User>lambdaQuery()
                .eq(User::getIsDeleted, false)
                // 关键字命中用户名或昵称之一（括号内 OR，整体与其它条件 AND）
                .and(hasKeyword, w -> w.like(User::getUsername, keyword)
                        .or().like(User::getNickname, keyword))
                .eq(role != null, User::getRole, role)
                .eq(status != null, User::getStatus, status)
                .orderByDesc(User::getUid);
        IPage<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        List<AdminUserVO> records = result.getRecords().stream().map(AdminUserVO::from).toList();
        return new PageVO<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AdminUserVO getDetail(Long uid) {
        return AdminUserVO.from(requireExisting(uid));
    }

    @Override
    public void ban(Long operatorUid, Long targetUid) {
        // 自我保护：管理员不能封禁自己，否则可能把自己锁在系统外
        if (operatorUid != null && operatorUid.equals(targetUid)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能封禁自己");
        }
        User user = requireExisting(targetUid);
        // 即使已是封禁态也继续执行吊销：覆盖「DB 已置 banned 但上次 Redis 吊销失败」的重试场景，
        // 不能因状态已 banned 就 return，否则漏吊销的旧会话会续命至 TTL（最长 24h）。
        if (user.getStatus() == null || user.getStatus() != STATUS_BANNED) {
            user.setStatus(STATUS_BANNED);
            userMapper.updateById(user);
        }
        // 作废其全部会话：手中令牌即时失效，封禁立即生效（幂等，重复调用无害）
        sessionService.deleteSessionsByUid(targetUid);
    }

    @Override
    public void unban(Long targetUid) {
        User user = requireExisting(targetUid);
        // 幂等：未封禁直接返回
        if (user.getStatus() == null || user.getStatus() == STATUS_NORMAL) {
            return;
        }
        user.setStatus(STATUS_NORMAL);
        userMapper.updateById(user);
        // 解封不动会话：封禁时已清空，用户凭新登录获取正常会话即可
    }

    @Override
    public void changeRole(Long operatorUid, Long targetUid, Integer newRole) {
        // 自我保护：管理员不能改自己的角色（如自我降级会立刻失去管理权）
        if (operatorUid != null && operatorUid.equals(targetUid)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能修改自己的角色");
        }
        User user = requireExisting(targetUid);
        user.setRole(newRole);
        userMapper.updateById(user);
        // 作废其全部会话：旧令牌缓存的角色随之失效，新角色重新登录后生效
        sessionService.deleteSessionsByUid(targetUid);
    }

    /**
     * 取未软删的用户实体，不存在或已软删则抛 404。
     *
     * @param uid 用户 uid
     * @return 用户实体
     */
    private User requireExisting(Long uid) {
        User user = userMapper.selectById(uid);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }
}
