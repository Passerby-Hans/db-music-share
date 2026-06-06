package com.music.song.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 移动歌曲到另一专辑的请求参数。
 *
 * <p>目标专辑须存在、未删、且为当前操作者本人创建（管理员除外）。
 * 移动后若源专辑为缺省专辑（is_default=true）且已无歌，则源缺省专辑
 * 一并软删（缺省专辑为单曲而生，空了即失去意义）；普通专辑空了不自动删。</p>
 */
public class SongMoveDTO {

    /** 目标专辑 aid：必填。 */
    @NotNull(message = "目标专辑不能为空")
    private Long targetAlbumAid;

    public Long getTargetAlbumAid() {
        return targetAlbumAid;
    }

    public void setTargetAlbumAid(Long targetAlbumAid) {
        this.targetAlbumAid = targetAlbumAid;
    }
}
