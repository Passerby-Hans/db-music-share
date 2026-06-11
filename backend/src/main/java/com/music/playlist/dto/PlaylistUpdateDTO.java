package com.music.playlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 更新歌单元信息请求体。
 *
 * <p>用于修改歌单的名称、简介、封面、公开性。{@link #playlistName} 与
 * {@link #isPublic} 必填（更新场景下公开性是明确的开关动作，不允许留空导致歧义）；
 * 简介与封面可选。字段长度上限对齐表定义。本 DTO 只覆盖元信息，不涉及歌单内
 * 曲目的增删（曲目走独立的加歌/移歌接口）。</p>
 */
public class PlaylistUpdateDTO {

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

    /** 是否公开（必填，明确的可见性开关）。 */
    @NotNull(message = "请指定歌单是否公开")
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
