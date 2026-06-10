package com.music.favorite.dto;

import com.music.song.entity.Song;

import java.time.OffsetDateTime;

/**
 * 我的收藏列表项响应数据。
 *
 * <p>在歌曲精简投影的基础上，附带两项收藏专属信息：</p>
 * <ul>
 *   <li>{@link #favTime} —— 该用户收藏这首歌的时间（列表按此倒序）；</li>
 *   <li>{@link #playable} —— 这首歌当前是否仍可正常播放/互动。</li>
 * </ul>
 *
 * <p><strong>失效歌曲策略</strong>：收藏列表<em>不过滤</em>失效歌曲——
 * 用户收藏过的歌即便后来被下架（软删）/驳回/未通过审核，仍如实列出，
 * 但 {@link #playable} 置为 {@code false}。前端据此将其置灰、拦截播放等
 * 互动操作并提示「该歌曲已下架」，而不是直接从列表里抹掉，
 * 保留「我收藏过什么」的完整记录。</p>
 *
 * <p>判定口径：{@code playable = (audit_status == 1 且 is_deleted == false)}，
 * 与歌曲「口径A 公开可见」一致。</p>
 */
public class FavoriteVO {

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

    /** 收藏时间。 */
    private OffsetDateTime favTime;

    /**
     * 当前是否可播放/互动。
     * {@code true}=歌曲已通过审核且未下架，可正常播放；
     * {@code false}=已下架/驳回/待审，前端应置灰并拦截播放。
     */
    private Boolean playable;

    /**
     * 由歌曲实体与收藏时间构建列表项 VO，并按口径A计算 {@link #playable}。
     *
     * @param song    歌曲实体（不可为 null）
     * @param favTime 收藏时间
     * @return 收藏列表项 VO
     */
    public static FavoriteVO from(Song song, OffsetDateTime favTime) {
        FavoriteVO vo = new FavoriteVO();
        vo.sid = song.getSid();
        vo.title = song.getTitle();
        vo.cover = song.getCover();
        vo.duration = song.getDuration();
        vo.playCount = song.getPlayCount();
        vo.albumAid = song.getAlbumAid();
        vo.uploaderUid = song.getUploaderUid();
        vo.favTime = favTime;
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

    public OffsetDateTime getFavTime() {
        return favTime;
    }

    public void setFavTime(OffsetDateTime favTime) {
        this.favTime = favTime;
    }

    public Boolean getPlayable() {
        return playable;
    }

    public void setPlayable(Boolean playable) {
        this.playable = playable;
    }
}
