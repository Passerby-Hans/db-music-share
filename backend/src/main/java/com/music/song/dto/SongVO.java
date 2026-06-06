package com.music.song.dto;

import com.music.song.entity.Song;

/**
 * 歌曲列表项响应数据（列表/分页展示用的精简投影）。
 *
 * <p>用于公开列表（口径A）与我的上传（口径B）。口径B会用到
 * {@link #auditStatus}/{@link #auditRemark} 展示审核进度，
 * 口径A的数据这两个字段恒为通过态，前端可忽略。</p>
 */
public class SongVO {

    /** 歌曲 sid。 */
    private Long sid;

    /** 标题。 */
    private String title;

    /** 封面路径。 */
    private String cover;

    /** 时长（秒）。 */
    private Integer duration;

    /** 冗余总点唱数。 */
    private Long playCount;

    /** 所属专辑 aid。 */
    private Long albumAid;

    /** 上传者 uid。 */
    private Long uploaderUid;

    /** 审核状态：0 待审 / 1 通过 / 2 驳回（口径B管理页用）。 */
    private Integer auditStatus;

    /** 驳回理由（口径B管理页用）。 */
    private String auditRemark;

    /**
     * 由歌曲实体构建列表项 VO。
     *
     * @param song 歌曲实体
     * @return 歌曲列表项 VO
     */
    public static SongVO from(Song song) {
        SongVO vo = new SongVO();
        vo.sid = song.getSid();
        vo.title = song.getTitle();
        vo.cover = song.getCover();
        vo.duration = song.getDuration();
        vo.playCount = song.getPlayCount();
        vo.albumAid = song.getAlbumAid();
        vo.uploaderUid = song.getUploaderUid();
        vo.auditStatus = song.getAuditStatus();
        vo.auditRemark = song.getAuditRemark();
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
}
