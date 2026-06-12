package com.music.user.controller;

import com.music.common.annotation.RequireRole;
import com.music.common.context.UserContext;
import com.music.common.result.PageVO;
import com.music.common.result.Result;
import com.music.user.dto.AdminUserVO;
import com.music.user.dto.UpdateRoleDTO;
import com.music.user.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口（管理后台）。
 *
 * <p>整类要求 role=2（管理员），由 {@link RequireRole} 在类级把关，
 * 路径前缀 {@code /api/admin/user}，风格与 {@code /api/admin/song}（歌曲审核）、
 * {@code /api/admin/storage}（孤儿扫描）一致。</p>
 *
 * <p>提供用户列表/详情查询，以及封禁、解封、改角色三类管理动作。
 * 当前管理员身份从 {@link UserContext}（会话拦截器写入）获取，
 * 用于「不能对自己执行封禁/改角色」的自我保护校验，不信任前端传入。</p>
 */
@RestController
@RequestMapping("/api/admin/user")
@RequireRole(2)
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * 分页查询用户列表，支持关键字搜索与角色/状态筛选。
     *
     * @param keyword 关键字（匹配用户名或昵称），可空
     * @param role    角色筛选（0/1/2），可空
     * @param status  状态筛选（0 正常 / 1 封禁），可空
     * @param page    页码，默认 1
     * @param size    每页条数，默认 10
     * @return 分页用户列表
     */
    @GetMapping
    public Result<PageVO<AdminUserVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer role,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(adminUserService.listUsers(keyword, role, status, page, size));
    }

    /**
     * 查询单个用户详情。
     *
     * @param uid 目标用户 uid
     * @return 用户视图（含封禁状态、不含密码）
     */
    @GetMapping("/{uid}")
    public Result<AdminUserVO> detail(@PathVariable Long uid) {
        return Result.success(adminUserService.getDetail(uid));
    }

    /**
     * 封禁用户：禁止其登录并即时踢下线（作废其全部会话）。
     *
     * @param uid 目标用户 uid
     * @return 成功响应
     */
    @PutMapping("/{uid}/ban")
    public Result<Void> ban(@PathVariable Long uid) {
        adminUserService.ban(UserContext.getUid(), uid);
        return Result.success();
    }

    /**
     * 解封用户：恢复其登录能力（需用户自行重新登录）。
     *
     * @param uid 目标用户 uid
     * @return 成功响应
     */
    @PutMapping("/{uid}/unban")
    public Result<Void> unban(@PathVariable Long uid) {
        adminUserService.unban(uid);
        return Result.success();
    }

    /**
     * 修改用户角色：变更后即时作废其会话，新角色重新登录后生效。
     *
     * @param uid 目标用户 uid
     * @param dto 角色参数（0/1/2，必填且合法）
     * @return 成功响应
     */
    @PutMapping("/{uid}/role")
    public Result<Void> changeRole(@PathVariable Long uid, @Valid @RequestBody UpdateRoleDTO dto) {
        adminUserService.changeRole(UserContext.getUid(), uid, dto.getRole());
        return Result.success();
    }
}
