package com.music.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发表评论请求参数。
 *
 * <p>支持两种形态：</p>
 * <ul>
 *   <li>主评论：仅传 {@link #sid} 与 {@link #content}，{@link #parentCid} 留空；</li>
 *   <li>回复：额外传 {@link #parentCid}（须为同一首歌下的主评论 cid）。</li>
 * </ul>
 *
 * <p>校验：{@link #sid} 必填；{@link #content} 非空且 ≤500 字。
 * 「parentCid 须存在、属同一 sid、且本身是主评论」依赖跨表/跨字段，
 * 单字段注解无法表达，放到 Service 层校验。评论者 uid 由 UserContext 提供，
 * 不在本 DTO 中（杜绝前端伪造他人身份发评论）。</p>
 */
public class CommentCreateDTO {

    /** 被评论的歌曲 sid。必填。 */
    @NotNull(message = "歌曲 sid 不能为空")
    private Long sid;

    /** 评论正文。非空、≤500 字。 */
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论内容长度不能超过 500 个字符")
    private String content;

    /**
     * 父评论 cid，可空。
     * 留空表示发表主评论；传值表示回复该主评论（两层盖楼）。
     */
    private Long parentCid;

    public Long getSid() {
        return sid;
    }

    public void setSid(Long sid) {
        this.sid = sid;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getParentCid() {
        return parentCid;
    }

    public void setParentCid(Long parentCid) {
        this.parentCid = parentCid;
    }
}
