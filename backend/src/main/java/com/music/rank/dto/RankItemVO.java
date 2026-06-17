package com.music.rank.dto;

/**
 * 排行榜单项响应数据。
 *
 * <p>榜单 Top N 的一项,含排名、歌曲展示信息(标题/封面直链/上传者昵称)
 * 与该榜单时间窗内的播放数 score。封面已由 service 层经
 * {@code storageService.publicUrl} 转为公开直链,前端 img 可直接用。</p>
 */
public class RankItemVO {

    /** 名次(从 1 起)。 */
    private int rank;

    /** 歌曲 sid。 */
    private Long sid;

    /** 标题。 */
    private String title;

    /** 封面公开直链。 */
    private String cover;

    /** 上传者昵称。 */
    private String uploaderName;

    /** 该榜单时间窗内的播放数。 */
    private long score;

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public Long getSid() {
        return sid;
    }

    public void setSid(Long sid) {
        this.sid = sid;
    }

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

    public String getUploaderName() {
        return uploaderName;
    }

    public void setUploaderName(String uploaderName) {
        this.uploaderName = uploaderName;
    }

    public long getScore() {
        return score;
    }

    public void setScore(long score) {
        this.score = score;
    }
}
