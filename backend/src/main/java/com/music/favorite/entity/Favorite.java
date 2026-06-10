package com.music.favorite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 收藏实体，对应数据库表 {@code favorite}。
 *
 * <p>表达「用户收藏音乐」的多对多联系：一行即一条收藏关系。
 * {@code (uid, sid)} 上有唯一约束（见 01_schema.sql R9），
 * 从数据库层面保证<strong>同一用户不能重复收藏同一首歌</strong>，
 * 业务层据此做幂等收藏。</p>
 *
 * <p>本表<strong>无软删除字段</strong>：取消收藏即物理删除该行。
 * 外键 {@code ON DELETE CASCADE} 兜底——删用户/删歌会级联清理其收藏记录。</p>
 */
@TableName("favorite")
public class Favorite {

    /**
     * 收藏主键 fid。
     * {@code IdType.AUTO} 对齐数据库 IDENTITY 自增策略。
     */
    @TableId(value = "fid", type = IdType.AUTO)
    private Long fid;

    /** 收藏者 uid。 */
    private Long uid;

    /** 被收藏的歌曲 sid。 */
    private Long sid;

    /** 收藏时间（列表按此倒序，最近收藏在前）。 */
    private OffsetDateTime favTime;

    public Long getFid() {
        return fid;
    }

    public void setFid(Long fid) {
        this.fid = fid;
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

    public OffsetDateTime getFavTime() {
        return favTime;
    }

    public void setFavTime(OffsetDateTime favTime) {
        this.favTime = favTime;
    }
}
