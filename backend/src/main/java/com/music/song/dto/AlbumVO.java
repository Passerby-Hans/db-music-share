package com.music.song.dto;

import com.music.song.entity.Album;

import java.time.LocalDate;

/**
 * 专辑响应数据。
 *
 * <p>用于专辑列表/详情。专辑详情接口会另带其下"口径A可见"的歌曲列表，
 * 由 Controller 组装，不在本 VO 内。</p>
 */
public class AlbumVO {

    /** 专辑 aid。 */
    private Long aid;

    /** 专辑名。 */
    private String albumName;

    /** 封面路径。 */
    private String cover;

    /** 发行日期。 */
    private LocalDate releaseDate;

    /** 简介。 */
    private String introduction;

    /** 是否缺省专辑。 */
    private Boolean isDefault;

    /** 创建者 uid。 */
    private Long creatorUid;

    /**
     * 由专辑实体构建 VO。
     *
     * @param album 专辑实体
     * @return 专辑 VO
     */
    public static AlbumVO from(Album album) {
        AlbumVO vo = new AlbumVO();
        vo.aid = album.getAid();
        vo.albumName = album.getAlbumName();
        vo.cover = album.getCover();
        vo.releaseDate = album.getReleaseDate();
        vo.introduction = album.getIntroduction();
        vo.isDefault = album.getIsDefault();
        vo.creatorUid = album.getCreatorUid();
        return vo;
    }

    public Long getAid() {
        return aid;
    }

    public void setAid(Long aid) {
        this.aid = aid;
    }

    public String getAlbumName() {
        return albumName;
    }

    public void setAlbumName(String albumName) {
        this.albumName = albumName;
    }

    public String getCover() {
        return cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getIntroduction() {
        return introduction;
    }

    public void setIntroduction(String introduction) {
        this.introduction = introduction;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public Long getCreatorUid() {
        return creatorUid;
    }

    public void setCreatorUid(Long creatorUid) {
        this.creatorUid = creatorUid;
    }
}
