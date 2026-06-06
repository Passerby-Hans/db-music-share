package com.music.song.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 修改歌曲元信息请求参数。
 *
 * <p>仅允许改可见元信息（标题/封面/时长/歌词），不含专辑归属
 * （移动专辑走独立接口）与审核态（由审核流程控制）。</p>
 *
 * <p>业务规则：上传者本人修改后，歌曲 {@code auditStatus} 重置为 0（待审），
 * 防止"先传干净内容过审、再偷改"；管理员修改不重置（可信）。</p>
 */
public class SongUpdateDTO {

    /** 标题：必填，≤150 字符。 */
    @NotBlank(message = "歌曲标题不能为空")
    @Size(max = 150, message = "标题长度不能超过 150 个字符")
    private String title;

    /** 封面路径：可空，≤255 字符。 */
    @Size(max = 255, message = "封面路径长度不能超过 255 个字符")
    private String cover;

    /** 时长（秒）：可空；若填写须为正数。 */
    @Positive(message = "时长须为正整数")
    private Integer duration;

    /** 歌词：可空。 */
    private String lyric;

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

    public String getLyric() {
        return lyric;
    }

    public void setLyric(String lyric) {
        this.lyric = lyric;
    }
}
