package com.music.comment.dto;

import java.time.OffsetDateTime;

/**
 * 回复列表项响应数据（某条主评论下的回复）。
 *
 * <p>用于 {@code GET /api/comment/{cid}/replies} 的分页结果。相比
 * {@link CommentVO}，回复本身不再嵌套子回复（两层盖楼），故不含
 * replyCount；其余字段含义一致。</p>
 *
 * <p>额外携带 {@link #parentCid}，便于前端在盖楼场景下展示"回复了谁"。
 * 评论者昵称/头像同样由 Service 层批量回填。</p>
 */
public class CommentReplyVO {

    /** 回复 cid。 */
    private Long cid;

    /** 所属主评论 cid。 */
    private Long parentCid;

    /** 回复者 uid。 */
    private Long uid;

    /** 回复者昵称（来自 app_user，可空）。 */
    private String nickname;

    /** 回复者头像 key/路径（来自 app_user，可空）。 */
    private String avatar;

    /** 回复正文。 */
    private String content;

    /** 冗余点赞数（当前恒为 0，占位）。 */
    private Integer likeCount;

    /** 回复时间。 */
    private OffsetDateTime createTime;

    public Long getCid() {
        return cid;
    }

    public void setCid(Long cid) {
        this.cid = cid;
    }

    public Long getParentCid() {
        return parentCid;
    }

    public void setParentCid(Long parentCid) {
        this.parentCid = parentCid;
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

    public OffsetDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(OffsetDateTime createTime) {
        this.createTime = createTime;
    }
}
