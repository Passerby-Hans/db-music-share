package com.music.stats.dto;

/**
 * 用户活跃度榜单项(管理后台统计)。
 *
 * <p>按点唱次数排行用户,展示昵称/头像直链与总点唱数。</p>
 */
public class TopUserVO {

    /** 名次(从 1 起)。 */
    private int rank;

    /** 用户 uid。 */
    private Long uid;

    /** 昵称。 */
    private String nickname;

    /** 头像公开直链。 */
    private String avatar;

    /** 该用户总点唱次数。 */
    private long playCount;

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

    public long getPlayCount() {
        return playCount;
    }

    public void setPlayCount(long playCount) {
        this.playCount = playCount;
    }
}
