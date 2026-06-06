package com.music.song.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 歌曲实体，对应数据库表 {@code song}。
 *
 * <p>每首歌必属唯一专辑（{@link #albumAid} 非空）。上传后默认进入
 * 待审状态（{@code auditStatus=0}），审核接口留待管理员模块。</p>
 *
 * <p>删除采用软删除（{@link #isDeleted}）。可见性三口径：
 * A 公开（auditStatus=1 且未删）/ B 我的上传（uploaderUid=本人且未删，任意审核态）/
 * C 管理员（无限制，留待管理员模块）。</p>
 */
@TableName("song")
public class Song {

    /**
     * 歌曲主键 sid。
     * {@code IdType.AUTO} 对齐数据库 IDENTITY 自增策略。
     */
    @TableId(value = "sid", type = IdType.AUTO)
    private Long sid;

    /** 标题。 */
    private String title;

    /** 封面文件路径，可空（本轮为字符串占位）。 */
    private String cover;

    /** 时长（秒），可空且须 > 0。 */
    private Integer duration;

    /** 歌词，可空。 */
    private String lyric;

    /** 音频文件路径（本轮为字符串占位）。 */
    private String audioPath;

    /** 冗余总点唱数。 */
    private Long playCount;

    /** 所属专辑 aid（非空，1:N 单一外键）。 */
    private Long albumAid;

    /** 上传者 uid（归属判定依据）。 */
    private Long uploaderUid;

    /** 审核状态：0 待审 / 1 通过 / 2 驳回。 */
    private Integer auditStatus;

    /** 驳回理由，可空。 */
    private String auditRemark;

    /** 软删除标记：true 表示已逻辑删除，前台不可见。 */
    private Boolean isDeleted;

    /** 上传时间。 */
    private OffsetDateTime createTime;

    public Long getSid() {
        return sid;
    }

    public void setSid(Long sid) {
        this.sid = sid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCover() {
        return cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public String getLyric() {
        return lyric;
    }

    public void setLyric(String lyric) {
        this.lyric = lyric;
    }

    public String getAudioPath() {
        return audioPath;
    }

    public void setAudioPath(String audioPath) {
        this.audioPath = audioPath;
    }

    public Long getPlayCount() {
        return playCount;
    }

    public void setPlayCount(Long playCount) {
        this.playCount = playCount;
    }

    public Long getAlbumAid() {
        return albumAid;
    }

    public void setAlbumAid(Long albumAid) {
        this.albumAid = albumAid;
    }

    public Long getUploaderUid() {
        return uploaderUid;
    }

    public void setUploaderUid(Long uploaderUid) {
        this.uploaderUid = uploaderUid;
    }

    public Integer getAuditStatus() {
        return auditStatus;
    }

    public void setAuditStatus(Integer auditStatus) {
        this.auditStatus = auditStatus;
    }

    public String getAuditRemark() {
        return auditRemark;
    }

    public void setAuditRemark(String auditRemark) {
        this.auditRemark = auditRemark;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public OffsetDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(OffsetDateTime createTime) {
        this.createTime = createTime;
    }
}
