package com.music.rank.service;

import com.music.rank.BoardType;
import com.music.rank.dto.RankItemVO;

import java.util.List;

/**
 * 排行榜服务。
 *
 * <p>两类能力:</p>
 * <ul>
 *   <li>{@link #list}:读榜——读 Redis ZSET(点唱 ZINCRBY 维护),空/异常降级聚合 play_record,
 *       批量回填歌曲信息,返回 Top10;</li>
 *   <li>{@link #rebuild}:对账重建——从 play_record 全量聚合,DEL+ZADD 覆盖写回 ZSET,
 *       消除漂移、重建丢失。由 {@code RankRebuildTask} 每日凌晨 + 启动时调用。</li>
 * </ul>
 */
public interface RankService {

    /**
     * 读取某榜单 Top10。
     *
     * @param board 榜单类型
     * @return 榜单项列表(按 score 倒序,最多 10 项;无数据返回空表)
     */
    List<RankItemVO> list(BoardType board);

    /**
     * 对账重建某榜单(从 play_record 全量聚合覆盖写回 Redis)。
     *
     * @param board 榜单类型
     */
    void rebuild(BoardType board);
}
