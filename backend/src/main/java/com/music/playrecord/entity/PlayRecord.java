package com.music.playrecord.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 点唱记录实体,对应数据库表 {@code play_record}。
 *
 * <p>每次合法点唱落一行,记录「谁(uid)、哪首歌(sid)、什么时间(play_time)」。
 * 它是排行榜(日/周榜按 play_time 聚合)与统计报表的<strong>真相源</strong>。
 * 本表无软删除字段、无唯一约束——同一用户对同一首歌可有多行(每次点唱一行),
 * 短时重复由业务层 60s 去重拦截(见 PlayRecordServiceImpl)。</p>
 *
 * <p>外键 {@code ON DELETE CASCADE}:物理删用户/删歌时级联清理。注意歌曲采用
 * <strong>软删除</strong>,软删不触发 CASCADE,故 play_record 明细保留、song.play_count
 * 也不变,二者始终一致(见设计文档 §6.4)。</p>
 */
@TableName("play_record")
public class PlayRecord {

    /** 点唱记录主键 pid,对齐数据库 IDENTITY 自增。 */
    @TableId(value = "pid", type = IdType.AUTO)
    private Long pid;

    /** 点唱者 uid。 */
    private Long uid;

    /** 被点唱的歌曲 sid。 */
    private Long sid;

    /** 点唱时间(排行榜日/周榜按此聚合;由 DB DEFAULT 填充,业务层亦可显式置入)。 */
    private OffsetDateTime playTime;

    public Long getPid() {
        return pid;
    }

    public void setPid(Long pid) {
        this.pid = pid;
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

    public OffsetDateTime getPlayTime() {
        return playTime;
    }

    public void setPlayTime(OffsetDateTime playTime) {
        this.playTime = playTime;
    }
}
