package com.music.playlist.dto;

import com.music.common.result.PageVO;

/**
 * 歌单详情响应数据。
 *
 * <p>组合两部分：</p>
 * <ul>
 *   <li>{@link #playlist} —— 歌单元信息（名称/简介/封面/公开性/曲目数等）；</li>
 *   <li>{@link #songs} —— 歌单内曲目分页列表，按加入时间倒序，每项带
 *       {@code playable} 标志（失效歌曲不剔除，仅置灰）。</li>
 * </ul>
 *
 * <p>曲目采用分页而非一次性返回，避免大歌单一次拉取过多数据；与专辑详情
 * 「album + songs」的组合结构思路一致。可见性（公开/私密）在 Service 层
 * 进入本 VO 组装<strong>之前</strong>已校验，能拿到本 VO 即代表有权查看。</p>
 */
public class PlaylistDetailVO {

    /** 歌单元信息（含曲目数）。 */
    private PlaylistVO playlist;

    /** 歌单内曲目分页（按加入时间倒序）。 */
    private PageVO<PlaylistSongVO> songs;

    /** 无参构造器。 */
    public PlaylistDetailVO() {
    }

    /**
     * 全参构造器。
     *
     * @param playlist 歌单元信息
     * @param songs    曲目分页
     */
    public PlaylistDetailVO(PlaylistVO playlist, PageVO<PlaylistSongVO> songs) {
        this.playlist = playlist;
        this.songs = songs;
    }

    public PlaylistVO getPlaylist() {
        return playlist;
    }

    public void setPlaylist(PlaylistVO playlist) {
        this.playlist = playlist;
    }

    public PageVO<PlaylistSongVO> getSongs() {
        return songs;
    }

    public void setSongs(PageVO<PlaylistSongVO> songs) {
        this.songs = songs;
    }
}
