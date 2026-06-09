package com.music.comment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 评论实体，对应数据库表 {@code comment}。
 *
 * <p>评论与评分解耦（评分在独立的 {@code rating} 表）；同一用户可对同一首歌
 * 多次评论。通过自引用外键 {@link #parentCid} 表达回复关系：</p>
 * <ul>
 *   <li>{@code parentCid == null} —— 主评论（楼主）；</li>
 *   <li>{@code parentCid != null} —— 回复，挂在某条主评论下。</li>
 * </ul>
 *
 * <p>本表<strong>无软删除字段</strong>：删除即物理删除。DB 侧通过
 * {@code ON DELETE CASCADE} 兜底——删歌/删用户会级联删其评论，
 * 删主评论会级联删其下全部回复（见 01_schema.sql R5）。</p>
 *
 * <p>楼层策略（本模块约定）：仅两层「主评论 + 回复」，不做无限嵌套。
 * 对回复再回复时，业务层会把它仍挂到同一主评论下（盖楼），
 * 即任何回复的 {@link #parentCid} 都指向一条主评论而非另一条回复。</p>
 */
@TableName("comment")
public class Comment {

    /**
     * 评论主键 cid。
     * {@code IdType.AUTO} 对齐数据库 IDENTITY 自增策略。
     */
    @TableId(value = "cid", type = IdType.AUTO)
    private Long cid;

    /** 评论者 uid。 */
    private Long uid;

    /** 被评论的歌曲 sid。 */
    private Long sid;

    /** 评论正文（非空）。 */
    private String content;

    /**
     * 冗余点赞数。
     * 当前模块未实现点赞（schema 无点赞关系表，无法防重复/取消），
     * 故恒为 0，作展示占位；点赞功能留待后续随收藏模块评估补表实现。
     */
    private Integer likeCount;

    /**
     * 父评论 cid，可空。
     * null 表示主评论；非 null 指向其所属主评论（两层盖楼，不指向回复）。
     */
    private Long parentCid;

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

    public Integer getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }

    public Long getParentCid() {
        return parentCid;
    }

    public void setParentCid(Long parentCid) {
        this.parentCid = parentCid;
    }

    public OffsetDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(OffsetDateTime createTime) {
        this.createTime = createTime;
    }
}
