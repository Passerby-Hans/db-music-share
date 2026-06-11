package com.music.rating.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 评分实体，对应数据库表 {@code rating}。
 *
 * <p>表达「用户给歌曲打分」的多对多联系：一行即一条评分记录。
 * {@code (uid, sid)} 上有唯一约束（见 01_schema.sql R6，对应设计决策③），
 * 从数据库层面保证<strong>同一用户对同一首歌仅能有一条评分</strong>。
 * 业务层据此做 upsert——「再次评分」即更新分数（改分），而非新增。</p>
 *
 * <p>{@code score} 取值 1~5，由 DB {@code CHECK (score BETWEEN 1 AND 5)} 兜底，
 * 应用层另用 {@code @Min/@Max} 提前校验。评分与评论<strong>解耦</strong>
 * （独立表，决策③）：一人对一首歌可只评分不评论、或只评论不评分。</p>
 *
 * <p>本表<strong>无软删除字段</strong>：撤销评分即物理删除该行。
 * 外键 {@code ON DELETE CASCADE} 兜底——删用户/删歌会级联清理其评分。</p>
 */
@TableName("rating")
public class Rating {

    /**
     * 评分主键 rid。
     * {@code IdType.AUTO} 对齐数据库 IDENTITY 自增策略。
     */
    @TableId(value = "rid", type = IdType.AUTO)
    private Long rid;

    /** 评分者 uid。 */
    private Long uid;

    /** 被评分的歌曲 sid。 */
    private Long sid;

    /** 分数，取值 1~5。 */
    private Integer score;

    /** 评分时间（改分时一并刷新；「我的评分」按此倒序）。 */
    private OffsetDateTime rateTime;

    public Long getRid() {
        return rid;
    }

    public void setRid(Long rid) {
        this.rid = rid;
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

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public OffsetDateTime getRateTime() {
        return rateTime;
    }

    public void setRateTime(OffsetDateTime rateTime) {
        this.rateTime = rateTime;
    }
}
