package com.music.song.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 新建专辑请求参数。
 *
 * <p>由上传者主动创建的普通专辑（is_default=false）。
 * 缺省专辑只由系统在上传单曲时自动生成，不经此接口。</p>
 */
public class AlbumCreateDTO {

    /** 专辑名：必填，≤100 字符。 */
    @NotBlank(message = "专辑名不能为空")
    @Size(max = 100, message = "专辑名长度不能超过 100 个字符")
    private String albumName;

    /** 封面路径：可空，≤255 字符。 */
    @Size(max = 255, message = "封面路径长度不能超过 255 个字符")
    private String cover;

    /** 发行日期：可空。 */
    private LocalDate releaseDate;

    /** 简介：可空。 */
    private String introduction;

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
}
