package com.music.song.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 歌曲审核请求参数（管理员用）。
 *
 * <p>仅对「待审」歌曲（{@code auditStatus=0}）操作：通过则置为已审核，
 * 驳回则置为驳回并记录理由。</p>
 *
 * <p>校验：{@link #pass} 必填；{@link #remark} 上限 255 字符。
 * 「驳回时 remark 必填」这一条件依赖另一字段取值，单字段注解无法表达，
 * 故放到 Service 层校验（见 SongService#audit），保持 DTO 校验简单直观。</p>
 */
public class SongAuditDTO {

    /** 审核结论：true 通过（auditStatus→1）/ false 驳回（auditStatus→2）。必填。 */
    @NotNull(message = "审核结论不能为空")
    private Boolean pass;

    /** 驳回理由：≤255 字符。驳回（pass=false）时必填，通过时忽略。 */
    @Size(max = 255, message = "驳回理由长度不能超过 255 个字符")
    private String remark;

    public Boolean getPass() {
        return pass;
    }

    public void setPass(Boolean pass) {
        this.pass = pass;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
