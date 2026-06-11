package com.music.playlist.dto;

import com.music.song.entity.Song;

import java.time.OffsetDateTime;

/**
 * 歌单内曲目项响应数据（歌单详情中的单首歌）。
 *
 * <p>在歌曲精简投影基础上附带两项歌单专属信息：</p>
 * <ul>
 *   <li>{@link #addTime} —— 这首歌加入歌单的时间（曲目按此倒序）；</li>
 *   <li>{@link #playable} —— 这首歌当前是否仍可正常播放。</li>
 * </ul>
 *
 * <p><strong>失效歌曲策略</strong>（与收藏列表一致）：歌单详情<em>不剔除</em>
 * 失效歌曲——加入后被下架（软删）/驳回的歌仍如实列出，但 {@link #playable}
 * 置为 {@code false}，前端据此置灰并拦截播放，保留歌单「收录过什么」的完整性。
 * 判定口径：{@code playable = (audit_status == 1 且 is_deleted == false)}，
 * 即歌曲「口径A 公开可见」。</p>
 */
public class PlaylistSongVO {

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

    /** 加入歌单的时间。 */
    private OffsetDateTime addTime;

    /**
     * 当前是否可播放。
     * {@code true}=已通过审核且未下架；{@code false}=已下架/驳回/待审，前端置灰拦播。
     */
    private Boolean playable;

    /**
     * 由歌曲实体与加入时间构建曲目项 VO，并按口径A计算 {@link #playable}。
     *
     * @param song    歌曲实体（不可为 null）
     * @param addTime 加入歌单的时间
     * @return 歌单曲目项 VO
     */
    public static PlaylistSongVO from(Song song, OffsetDateTime addTime) {
        PlaylistSongVO vo = new PlaylistSongVO();
        vo.sid = song.getSid();
        vo.title = song.getTitle();
        vo.cover = song.getCover();
        vo.duration = song.getDuration();
        vo.playCount = song.getPlayCount();
        vo.albumAid = song.getAlbumAid();
        vo.uploaderUid = song.getUploaderUid();
        vo.addTime = addTime;
        // 口径A：通过审核(1) 且 未软删 才可播放；其余一律置灰
        vo.playable = song.getAuditStatus() != null
                && song.getAuditStatus() == 1
                && !Boolean.TRUE.equals(song.getIsDeleted());
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

    public OffsetDateTime getAddTime() {
        return addTime;
    }

    public void setAddTime(OffsetDateTime addTime) {
        this.addTime = addTime;
    }

    public Boolean getPlayable() {
        return playable;
    }

    public void setPlayable(Boolean playable) {
        this.playable = playable;
    }
}
