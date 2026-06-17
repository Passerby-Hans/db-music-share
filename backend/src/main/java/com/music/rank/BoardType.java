package com.music.rank;

/**
 * 排行榜榜单类型。
 *
 * <p>总榜(全量累计)/日榜(当天时间窗)/周榜(本周时间窗),对应点唱模块写入的
 * 三个 Redis ZSET:{@code rank:total} / {@code rank:daily:yyyy-MM-dd} /
 * {@code rank:weekly:yyyy-Www}。</p>
 */
public enum BoardType {
    /** 总榜:全量累计播放数。 */
    TOTAL,
    /** 日榜:当天 0 点起的播放数。 */
    DAILY,
    /** 周榜:本周一 0 点起的播放数(ISO 周)。 */
    WEEKLY
}
