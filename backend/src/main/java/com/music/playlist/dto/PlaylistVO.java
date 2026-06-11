package com.music.playlist.dto;

import com.music.playlist.entity.Playlist;

import java.time.OffsetDateTime;

/**
 * 歌单列表项响应数据（用于「我的歌单」与「公开歌单广场」列表）。
 *
 * <p>在歌单元信息基础上附带 {@link #songCount} 曲目数，供列表卡片直接展示
 * 「N 首」。列表场景不返回具体曲目，曲目明细由歌单详情接口按需分页加载。</p>
 */
public class PlaylistVO {

    /** 歌单 plid。 */
    private Long plid;

    /** 创建者 uid。 */
    private Long uid;

    /** 歌单名称。 */
    private String playlistName;

    /** 简介。 */
    private String description;

    /** 封面路径。 */
    private String cover;

    /** 是否公开。 */
    private Boolean isPublic;

    /** 创建时间。 */
    private OffsetDateTime createTime;

    /** 歌单内曲目数（含已失效歌曲，反映歌单收录总量）。 */
    private Long songCount;

    /**
     * 由歌单实体与曲目数构建列表项 VO。
     *
     * @param p         歌单实体（不可为 null）
     * @param songCount 该歌单曲目数
     * @return 歌单列表项 VO
     */
    public static PlaylistVO from(Playlist p, long songCount) {
        PlaylistVO vo = new PlaylistVO();
        vo.plid = p.getPlid();
        vo.uid = p.getUid();
        vo.playlistName = p.getPlaylistName();
        vo.description = p.getDescription();
        vo.cover = p.getCover();
        vo.isPublic = p.getIsPublic();
        vo.createTime = p.getCreateTime();
        vo.songCount = songCount;
        return vo;
    }

    public Long getPlid() {
        return plid;
    }

    public void setPlid(Long plid) {
        this.plid = plid;
    }

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public String getPlaylistName() {
        return playlistName;
    }

    public void setPlaylistName(String playlistName) {
        this.playlistName = playlistName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCover() {
        return cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public OffsetDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(OffsetDateTime createTime) {
        this.createTime = createTime;
    }

    public Long getSongCount() {
        return songCount;
    }

    public void setSongCount(Long songCount) {
        this.songCount = songCount;
    }
}
