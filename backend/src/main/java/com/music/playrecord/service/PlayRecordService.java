package com.music.playrecord.service;

/**
 * 点唱记录服务。
 *
 * <p>仅暴露「记录一次点唱」能力。点唱是排行榜与统计的数据生产者;
 * 榜单读接口、对账、降级均归后续排行榜阶段,不在本服务。</p>
 */
public interface PlayRecordService {

    /**
     * 记录一次合法点唱:校验歌曲口径A可见、60s 去重、事务内写明细 + play_count+1、
     * Redis 三榜 ZINCRBY。
     *
     * @param uid 点唱者 uid(取自服务端会话,不信任前端)
     * @param sid 被点唱的歌曲 sid
     */
    void record(Long uid, Long sid);
}
