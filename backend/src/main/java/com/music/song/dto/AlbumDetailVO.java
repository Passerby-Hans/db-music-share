package com.music.song.dto;

import java.util.List;

/**
 * 专辑详情响应数据：专辑信息 + 其下"口径A可见"歌曲列表。
 *
 * <p>用于公开专辑详情接口，把专辑元信息与可见曲目一次性返回，
 * 省去前端二次请求。</p>
 */
public class AlbumDetailVO {

    /** 专辑信息。 */
    private AlbumVO album;

    /** 该专辑下已审核且未删的歌曲列表。 */
    private List<SongVO> songs;

    /** 无参构造器（序列化需要）。 */
    public AlbumDetailVO() {
    }

    /**
     * 全参构造器。
     *
     * @param album 专辑信息
     * @param songs 可见歌曲列表
     */
    public AlbumDetailVO(AlbumVO album, List<SongVO> songs) {
        this.album = album;
        this.songs = songs;
    }

    public AlbumVO getAlbum() {
        return album;
    }

    public void setAlbum(AlbumVO album) {
        this.album = album;
    }

    public List<SongVO> getSongs() {
        return songs;
    }

    public void setSongs(List<SongVO> songs) {
        this.songs = songs;
    }
}
