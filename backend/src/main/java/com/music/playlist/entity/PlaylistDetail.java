package com.music.playlist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 歌单详情实体，对应数据库表 {@code playlist_detail}。
 *
 * <p>表达「歌单收录歌曲」的多对多联系：一行即「某歌单收录了某首歌」。
 * {@code (plid, sid)} 上有唯一约束（见 01_schema.sql R8），从数据库层面保证
 * <strong>同一歌单不会重复收录同一首歌</strong>，业务层据此做幂等加歌。</p>
 *
 * <p>两个外键均带 {@code ON DELETE CASCADE}：删歌单清理其全部曲目记录，
 * 删歌曲（物理删除时）清理其在各歌单中的收录记录。本表<strong>无软删除字段</strong>，
 * 移出歌单即物理删除该行。</p>
 */
@TableName("playlist_detail")
public class PlaylistDetail {

    /**
     * 歌单详情主键 pdid。
     * {@code IdType.AUTO} 对齐数据库 IDENTITY 自增策略。
     */
    @TableId(value = "pdid", type = IdType.AUTO)
    private Long pdid;

    /** 所属歌单 plid。 */
    private Long plid;

    /** 收录的歌曲 sid。 */
    private Long sid;

    /** 加入歌单的时间（歌单内曲目按此倒序，最近加入在前）。 */
    private OffsetDateTime addTime;

    public Long getPdid() {
        return pdid;
    }

    public void setPdid(Long pdid) {
        this.pdid = pdid;
    }

    public Long getPlid() {
        return plid;
    }

    public void setPlid(Long plid) {
        this.plid = plid;
    }

    public Long getSid() {
        return sid;
    }

    public void setSid(Long sid) {
        this.sid = sid;
    }

    public OffsetDateTime getAddTime() {
        return addTime;
    }

    public void setAddTime(OffsetDateTime addTime) {
        this.addTime = addTime;
    }
}
