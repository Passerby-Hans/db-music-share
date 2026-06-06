package com.music.song.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;

/**
 * 专辑实体，对应数据库表 {@code album}。
 *
 * <p>专辑与歌曲为 1:N（一张专辑含多首歌，一首歌必属唯一专辑）。
 * {@code isDefault=true} 表示为"单曲"自动生成的独立缺省专辑——
 * 用户上传歌曲时若既未指定已有专辑、也未当场新建专辑，
 * 系统会为这首歌单独生成一张缺省专辑（专辑名取歌名）。</p>
 *
 * <p>删除采用软删除（{@link #isDeleted}），与 {@code song} 表对称；
 * 物理外键 ON DELETE CASCADE 仅作兜底清理。</p>
 */
@TableName("album")
public class Album {

    /**
     * 专辑主键 aid。
     * {@code IdType.AUTO} 对齐数据库 IDENTITY 自增策略。
     */
    @TableId(value = "aid", type = IdType.AUTO)
    private Long aid;

    /** 专辑名。 */
    private String albumName;

    /** 封面文件路径，可空（本轮为字符串占位）。 */
    private String cover;

    /** 发行日期，可空。 */
    private LocalDate releaseDate;

    /** 简介，可空。 */
    private String introduction;

    /** 是否为单曲自动生成的缺省专辑：true 缺省 / false 普通。 */
    private Boolean isDefault;

    /** 创建者 uid（归属判定依据）。 */
    private Long creatorUid;

    /** 软删除标记：true 表示已逻辑删除，前台不可见。 */
    private Boolean isDeleted;

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

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }
}
