package com.music.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 管理员修改用户角色的请求体。
 *
 * <p>角色取值约束为 0/1/2 三档（0 普通用户 / 1 上传者 / 2 管理员），
 * 由 {@code @NotNull + @Min(0) + @Max(2)} 在入参层拦掉缺失与越界，
 * 非法值直接返回 400，不进入业务逻辑。</p>
 */
public class UpdateRoleDTO {

    /** 目标角色码：0 普通用户 / 1 上传者 / 2 管理员。 */
    @NotNull(message = "角色不能为空")
    @Min(value = 0, message = "角色码非法")
    @Max(value = 2, message = "角色码非法")
    private Integer role;

    public Integer getRole() {
        return role;
    }

    public void setRole(Integer role) {
        this.role = role;
    }
}
