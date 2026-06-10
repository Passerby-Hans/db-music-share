package com.music.comment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 评论点赞实体，对应数据库表 {@code comment_like}。
 *
 * <p>表达「用户点赞评论」的多对多联系，与 {@code favorite}/{@code rating} 同构：
 * {@code (uid, cid)} 上有唯一约束（见 01_schema.sql R10），从数据库层面保证
 * <strong>同一用户对同一条评论仅能点赞一次</strong>，业务层据此做幂等点赞。</p>
 *
 * <p>点赞数采用<strong>实时 {@code COUNT(*)} 统计</strong>（与回复数同模式），
 * 不依赖 {@code comment.like_count} 冗余字段——该字段在引入本表后已闲置。</p>
 *
 * <p>本表<strong>无软删除字段</strong>：取消点赞即物理删除该行。外键
 * {@code ON DELETE CASCADE} 兜底——删评论/删用户会级联清理其点赞记录。</p>
 */
@TableName("comment_like")
public class CommentLike {

    /**
     * 点赞主键 clid。
     * {@code IdType.AUTO} 对齐数据库 IDENTITY 自增策略。
     */
    @TableId(value = "clid", type = IdType.AUTO)
    private Long clid;

    /** 点赞者 uid。 */
    private Long uid;

    /** 被点赞的评论 cid。 */
    private Long cid;

    /** 点赞时间。 */
    private OffsetDateTime likeTime;

    public Long getClid() {
        return clid;
    }

    public void setClid(Long clid) {
        this.clid = clid;
    }

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public Long getCid() {
        return cid;
    }

    public void setCid(Long cid) {
        this.cid = cid;
    }

    public OffsetDateTime getLikeTime() {
        return likeTime;
    }

    public void setLikeTime(OffsetDateTime likeTime) {
        this.likeTime = likeTime;
    }
}
