package com.music.stats.dto;

/**
 * 上传者贡献榜单项(管理后台统计)。
 *
 * <p>按其上传音乐的总播放量排行上传者,展示昵称/头像直链、音乐数量与总播放量。</p>
 */
public class TopUploaderVO {

    /** 名次(从 1 起)。 */
    private int rank;

    /** 上传者 uid。 */
    private Long uid;

    /** 昵称。 */
    private String nickname;

    /** 头像公开直链。 */
    private String avatar;

    /** 上传的音乐数量(未删)。 */
    private long songCount;

    /** 这些音乐的总播放量。 */
    private long totalPlayCount;

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public long getSongCount() {
        return songCount;
    }

    public void setSongCount(long songCount) {
        this.songCount = songCount;
    }

    public long getTotalPlayCount() {
        return totalPlayCount;
    }

    public void setTotalPlayCount(long totalPlayCount) {
        this.totalPlayCount = totalPlayCount;
    }
}
