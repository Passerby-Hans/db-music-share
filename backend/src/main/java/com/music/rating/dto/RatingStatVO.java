package com.music.rating.dto;

/**
 * 某首歌的评分概况响应数据。
 *
 * <p>供歌曲详情页展示「评分」区块：平均分、参与评分人数，以及当前登录用户
 * 自己打的分（用于回显高亮/可改）。本接口走<strong>软鉴权</strong>：</p>
 * <ul>
 *   <li>游客访问 —— {@link #myScore} 为 {@code null}（未登录无个人评分）；</li>
 *   <li>登录用户访问 —— {@link #myScore} 回填其评分，未评过则为 {@code null}。</li>
 * </ul>
 *
 * <p>{@link #avgScore} 为 {@code AVG(score)} 结果，保留一位小数便于展示；
 * 无人评分时平均分为 {@code 0} 且 {@link #ratingCount} 为 {@code 0}。</p>
 */
public class RatingStatVO {

    /** 歌曲 sid。 */
    private Long sid;

    /** 平均分（保留一位小数；无人评分时为 0）。 */
    private Double avgScore;

    /** 参与评分的人数。 */
    private Long ratingCount;

    /** 当前用户自己的评分；游客或未评过为 null。 */
    private Integer myScore;

    /** 无参构造器。 */
    public RatingStatVO() {
    }

    /**
     * 全参构造器。
     *
     * @param sid         歌曲 sid
     * @param avgScore    平均分
     * @param ratingCount 评分人数
     * @param myScore     我的评分（可为 null）
     */
    public RatingStatVO(Long sid, Double avgScore, Long ratingCount, Integer myScore) {
        this.sid = sid;
        this.avgScore = avgScore;
        this.ratingCount = ratingCount;
        this.myScore = myScore;
    }

    public Long getSid() {
        return sid;
    }

    public void setSid(Long sid) {
        this.sid = sid;
    }

    public Double getAvgScore() {
        return avgScore;
    }

    public void setAvgScore(Double avgScore) {
        this.avgScore = avgScore;
    }

    public Long getRatingCount() {
        return ratingCount;
    }

    public void setRatingCount(Long ratingCount) {
        this.ratingCount = ratingCount;
    }

    public Integer getMyScore() {
        return myScore;
    }

    public void setMyScore(Integer myScore) {
        this.myScore = myScore;
    }
}
