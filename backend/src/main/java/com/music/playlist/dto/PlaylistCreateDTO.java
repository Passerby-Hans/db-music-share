package com.music.playlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建歌单请求体。
 *
 * <p>仅 {@link #playlistName} 必填，其余可选。{@link #isPublic} 允许为
 * {@code null}：为空时由 Service 兜底为默认公开（{@code true}），
 * 对齐数据库 {@code is_public DEFAULT TRUE}。字段长度上限对齐表定义
 * （name VARCHAR(100)、description/cover VARCHAR(255)），避免插入时被 DB 截断报错。</p>
 */
public class PlaylistCreateDTO {

    /** 歌单名称（必填，1~100 字）。 */
    @NotBlank(message = "歌单名称不能为空")
    @Size(max = 100, message = "歌单名称不能超过100字")
    private String playlistName;

    /** 歌单简介（可选，最多 255 字）。 */
    @Size(max = 255, message = "歌单简介不能超过255字")
    private String description;

    /** 歌单封面路径（可选，最多 255 字）。 */
    @Size(max = 255, message = "封面路径不能超过255字")
    private String cover;

    /** 是否公开（可选）。为 null 时 Service 兜底为 true（默认公开）。 */
    private Boolean isPublic;

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
}
