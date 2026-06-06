package com.music.song.dto;

import com.music.song.entity.Song;

import java.time.OffsetDateTime;

/**
 * 歌曲详情响应数据（详情页的完整投影）。
 *
 * <p>相比列表项 {@link SongVO}，额外含歌词、音频路径、上传时间等
 * 详情字段。</p>
 */
public class SongDetailVO {

    /** 歌曲 sid。 */
    private Long sid;

    /** 标题。 */
    private String title;

    /** 封面路径。 */
    private String cover;

    /** 时长（秒）。 */
    private Integer duration;

    /** 歌词。 */
    private String lyric;

    /** 音频文件路径。 */
    private String audioPath;

    /** 冗余总点唱数。 */
    private Long playCount;

    /** 所属专辑 aid。 */
    private Long albumAid;

    /** 上传者 uid。 */
    private Long uploaderUid;

    /** 审核状态：0 待审 / 1 通过 / 2 驳回。 */
    private Integer auditStatus;

    /** 驳回理由。 */
    private String auditRemark;

    /** 上传时间。 */
    private OffsetDateTime createTime;

    /**
     * 由歌曲实体构建详情 VO。
     *
     * @param song 歌曲实体
     * @return 歌曲详情 VO
     */
    public static SongDetailVO from(Song song) {
        SongDetailVO vo = new SongDetailVO();
        vo.sid = song.getSid();
        vo.title = song.getTitle();
        vo.cover = song.getCover();
        vo.duration = song.getDuration();
        vo.lyric = song.getLyric();
        vo.audioPath = song.getAudioPath();
        vo.playCount = song.getPlayCount();
        vo.albumAid = song.getAlbumAid();
        vo.uploaderUid = song.getUploaderUid();
        vo.auditStatus = song.getAuditStatus();
        vo.auditRemark = song.getAuditRemark();
        vo.createTime = song.getCreateTime();
        return vo;
    }

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

    public OffsetDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(OffsetDateTime createTime) {
        this.createTime = createTime;
    }
}
