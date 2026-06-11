package com.music.rating.dto;

import com.music.song.entity.Song;

import java.time.OffsetDateTime;

/**
 * 我的评分列表项响应数据。
 *
 * <p>在歌曲精简投影基础上附带评分专属信息：</p>
 * <ul>
 *   <li>{@link #score} —— 我给这首歌打的分（1~5）；</li>
 *   <li>{@link #rateTime} —— 我的评分时间（列表按此倒序）；</li>
 *   <li>{@link #playable} —— 这首歌当前是否仍可正常播放。</li>
 * </ul>
 *
 * <p><strong>失效歌曲策略</strong>（与收藏列表一致）：我的评分列表<em>不剔除</em>
 * 失效歌曲——评分后被下架（软删）/驳回的歌仍如实列出，但 {@link #playable}
 * 置为 {@code false}，前端据此置灰拦播，保留「我评过什么」的完整记录。
 * 判定口径：{@code playable = (audit_status == 1 且 is_deleted == false)}。</p>
 */
public class MyRatingVO {

    /** 歌曲 sid。 */
    private Long sid;

    /** 标题。 */
    private String title;

    /** 封面路径。 */
    private String cover;

    /** 时长（秒）。 */
    private Integer duration;

    /** 所属专辑 aid。 */
    private Long albumAid;

    /** 上传者 uid。 */
    private Long uploaderUid;

    /** 我给这首歌的评分（1~5）。 */
    private Integer score;

    /** 我的评分时间。 */
    private OffsetDateTime rateTime;

    /**
     * 当前是否可播放。
     * {@code true}=已通过审核且未下架；{@code false}=已下架/驳回/待审，前端置灰拦播。
     */
    private Boolean playable;

    /**
     * 由歌曲实体、我的分数与评分时间构建列表项 VO，并按口径A计算 {@link #playable}。
     *
     * @param song     歌曲实体（不可为 null）
     * @param score    我的评分
     * @param rateTime 我的评分时间
     * @return 我的评分列表项 VO
     */
    public static MyRatingVO from(Song song, Integer score, OffsetDateTime rateTime) {
        MyRatingVO vo = new MyRatingVO();
        vo.sid = song.getSid();
        vo.title = song.getTitle();
        vo.cover = song.getCover();
        vo.duration = song.getDuration();
        vo.albumAid = song.getAlbumAid();
        vo.uploaderUid = song.getUploaderUid();
        vo.score = score;
        vo.rateTime = rateTime;
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

    public Boolean getPlayable() {
        return playable;
    }

    public void setPlayable(Boolean playable) {
        this.playable = playable;
    }
}
