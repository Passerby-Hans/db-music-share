package com.music.song.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 上传歌曲请求参数。
 *
 * <p>专辑归属三选一（互斥，优先级从上到下）：</p>
 * <ol>
 *   <li>指定 {@link #albumAid}：放入该已有专辑（须本人创建、未删）；</li>
 *   <li>指定 {@link #newAlbumName}（不传 albumAid）：当场新建普通专辑并放入；</li>
 *   <li>两者都不传：为这首歌自动生成一张独立缺省专辑（专辑名取歌名）。</li>
 * </ol>
 * <p>{@code albumAid} 与 {@code newAlbumName} 同时给出视为非法（400）。</p>
 */
public class SongUploadDTO {

    /** 标题：必填，≤150 字符。 */
    @NotBlank(message = "歌曲标题不能为空")
    @Size(max = 150, message = "标题长度不能超过 150 个字符")
    private String title;

    /** 音频文件路径：必填，≤255 字符（本轮为字符串占位）。 */
    @NotBlank(message = "音频路径不能为空")
    @Size(max = 255, message = "音频路径长度不能超过 255 个字符")
    private String audioPath;

    /** 封面路径：可空，≤255 字符。 */
    @Size(max = 255, message = "封面路径长度不能超过 255 个字符")
    private String cover;

    /** 时长（秒）：可空；若填写须为正数。 */
    @Positive(message = "时长须为正整数")
    private Integer duration;

    /** 歌词：可空。 */
    private String lyric;

    /** 模式①：放入的已有专辑 aid（与 newAlbumName 互斥）。 */
    private Long albumAid;

    /** 模式②：当场新建的专辑名（与 albumAid 互斥），≤100 字符。 */
    @Size(max = 100, message = "专辑名长度不能超过 100 个字符")
    private String newAlbumName;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAudioPath() {
        return audioPath;
    }

    public void setAudioPath(String audioPath) {
        this.audioPath = audioPath;
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

    public String getLyric() {
        return lyric;
    }

    public void setLyric(String lyric) {
        this.lyric = lyric;
    }

    public Long getAlbumAid() {
        return albumAid;
    }

    public void setAlbumAid(Long albumAid) {
        this.albumAid = albumAid;
    }

    public String getNewAlbumName() {
        return newAlbumName;
    }

    public void setNewAlbumName(String newAlbumName) {
        this.newAlbumName = newAlbumName;
    }
}
