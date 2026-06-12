package com.music.user.service;

import com.music.common.result.PageVO;
import com.music.user.dto.AdminUserVO;

/**
 * 管理后台的用户管理业务接口。
 *
 * <p>与面向用户自身的 {@link UserService} 分离：本接口下的操作均为
 * <b>管理员（role=2）</b>对其他账号的管理行为——查询、封禁/解封、改角色，
 * 由控制器层的 {@code @RequireRole(2)} 统一把关。</p>
 *
 * <p><b>即时生效</b>：封禁与改角色会调用会话服务作废目标用户的全部会话，
 * 使其手中令牌立刻失效——这是 Session+Redis 相较 JWT 的核心优势。</p>
 */
public interface AdminUserService {

    /**
     * 分页查询用户列表，支持按关键字（用户名或昵称）模糊搜索，并可按角色/状态筛选。
     *
     * <p>列表含已封禁用户（管理员需要管理它们），但<b>不含已软删用户</b>。</p>
     *
     * @param keyword 关键字，匹配用户名或昵称；为空则不限
     * @param role    角色筛选（0/1/2）；为 {@code null} 则不限
     * @param status  状态筛选（0 正常 / 1 封禁）；为 {@code null} 则不限
     * @param page    页码，从 1 起
     * @param size    每页条数
     * @return 分页用户视图列表
     */
    PageVO<AdminUserVO> listUsers(String keyword, Integer role, Integer status, long page, long size);

    /**
     * 查询单个用户的管理视图详情。
     *
     * @param uid 目标用户 uid
     * @return 用户视图
     * @throws com.music.common.exception.BizException 用户不存在或已软删（404）
     */
    AdminUserVO getDetail(Long uid);

    /**
     * 封禁用户：置 status=1，并作废其全部会话使其即时下线。
     *
     * <p>幂等：已封禁的用户再次封禁直接成功。</p>
     *
     * @param operatorUid 当前管理员 uid（用于自我保护校验）
     * @param targetUid   目标用户 uid
     * @throws com.music.common.exception.BizException 目标不存在（404）、或封禁自己（400）
     */
    void ban(Long operatorUid, Long targetUid);

    /**
     * 解封用户：置 status=0。解封不涉及会话（封禁时已清空，重新登录即可）。
     *
     * <p>幂等：未封禁的用户解封直接成功。</p>
     *
     * @param targetUid 目标用户 uid
     * @throws com.music.common.exception.BizException 目标不存在（404）
     */
    void unban(Long targetUid);

    /**
     * 修改用户角色，并作废其全部会话使新角色即时生效（旧令牌内缓存的角色作废）。
     *
     * @param operatorUid 当前管理员 uid（用于自我保护校验）
     * @param targetUid   目标用户 uid
     * @param newRole     新角色码（0/1/2，已由 DTO 校验合法）
     * @throws com.music.common.exception.BizException 目标不存在（404）、或修改自己角色（400）
     */
    void changeRole(Long operatorUid, Long targetUid, Integer newRole);
}
