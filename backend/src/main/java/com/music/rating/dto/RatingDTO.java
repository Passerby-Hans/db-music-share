package com.music.rating.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 提交评分请求体。
 *
 * <p>仅含一个分数字段。{@link #score} 必填且限定 1~5，应用层用
 * {@code @NotNull + @Min/@Max} 提前拦截非法值（返回 400），与数据库
 * {@code CHECK (score BETWEEN 1 AND 5)} 形成双重保障——后者兜底防绕过应用直插库。</p>
 *
 * <p>歌曲 sid 走路径参数（{@code POST /api/rating/{sid}}）而非本 DTO，
 * 与收藏/歌单加歌的资源定位风格一致。</p>
 */
public class RatingDTO {

    /** 分数，必填，取值 1~5。 */
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低 1 分")
    @Max(value = 5, message = "评分最高 5 分")
    private Integer score;

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }
}
