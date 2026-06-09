package com.music.comment.dto;

import java.time.OffsetDateTime;

/**
 * 主评论列表项响应数据（某首歌评论区的"楼"）。
 *
 * <p>用于 {@code GET /api/comment/song/{sid}} 的分页结果。除评论本身字段外，
 * 携带评论者展示信息（{@link #nickname}/{@link #avatar}）与该楼的回复数
 * {@link #replyCount}，供前端先展示主评论列表、点开再拉取回复。</p>
 *
 * <p>评论者信息与回复数并非 comment 单表字段，由 Service 层批量查询
 * （按 uid 查用户、按 parentCid 聚合计数）后填充，规避逐条查询的 N+1。</p>
 */
public class CommentVO {

    /** 评论 cid。 */
    private Long cid;

    /** 评论者 uid。 */
    private Long uid;

    /** 评论者昵称（来自 app_user，可能因用户已删而为空）。 */
    private String nickname;

    /** 评论者头像 key/路径（来自 app_user，可空）。 */
    private String avatar;

    /** 评论正文。 */
    private String content;

    /** 冗余点赞数（当前恒为 0，占位）。 */
    private Integer likeCount;

    /** 该主评论下的回复数量。 */
    private Long replyCount;

    /** 评论时间。 */
    private OffsetDateTime createTime;

    public Long getCid() {
        return cid;
    }

    public void setCid(Long cid) {
        this.cid = cid;
    }

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }

    public Long getReplyCount() {
        return replyCount;
    }

    public void setReplyCount(Long replyCount) {
        this.replyCount = replyCount;
    }

    public OffsetDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(OffsetDateTime createTime) {
        this.createTime = createTime;
    }
}
