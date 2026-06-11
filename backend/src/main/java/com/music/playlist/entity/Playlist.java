package com.music.playlist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 歌单实体，对应数据库表 {@code playlist}。
 *
 * <p>表达「用户私有音乐集合」：一行即一个歌单，归属某个用户（{@code uid}）。
 * 歌单与歌曲之间通过 {@code playlist_detail} 维护多对多关系（见 {@link PlaylistDetail}）。
 * 外键 {@code uid → app_user} 带 {@code ON DELETE CASCADE}：删用户会级联清理其歌单。</p>
 *
 * <p><strong>公开/私密</strong>：{@link #isPublic} 控制可见性——公开歌单任何登录用户
 * 可浏览，私密歌单仅创建者本人（及管理员）可见。本表<strong>无软删除字段</strong>，
 * 删歌单即物理删除，其下 {@code playlist_detail} 由 DB 级联删除兜底。</p>
 */
@TableName("playlist")
public class Playlist {

    /**
     * 歌单主键 plid。
     * {@code IdType.AUTO} 对齐数据库 IDENTITY 自增策略。
     */
    @TableId(value = "plid", type = IdType.AUTO)
    private Long plid;

    /** 歌单创建者 uid（归属用户）。 */
    private Long uid;

    /** 歌单名称。 */
    private String playlistName;

    /** 歌单简介（可空）。 */
    private String description;

    /** 歌单封面路径（可空）。 */
    private String cover;

    /**
     * 是否公开。
     * {@code true}=公开（任何登录用户可浏览）；{@code false}=私密（仅 owner/管理员可见）。
     */
    private Boolean isPublic;

    /** 创建时间（「我的歌单」按此倒序，最近创建在前）。 */
    private OffsetDateTime createTime;

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
}
