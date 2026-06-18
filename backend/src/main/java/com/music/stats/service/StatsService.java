package com.music.stats.service;

import com.music.stats.dto.TopUploaderVO;
import com.music.stats.dto.TopUserVO;

import java.util.List;

/**
 * 统计报表服务。
 *
 * <p>两类管理视角统计:</p>
 * <ul>
 *   <li>{@link #topUsers}:用户活跃度 TOP10(按点唱次数),读 Redis 缓存,miss 实时聚合 play_record;</li>
 *   <li>{@link #topUploaders}:上传者贡献 TOP10(按总播放量),读缓存,miss 实时聚合 song。</li>
 * </ul>
 *
 * <p>{@link #refreshAll} 由 {@code StatsSyncTask} 每小时 + 启动时调用,覆盖刷新两缓存。</p>
 */
public interface StatsService {

    /**
     * 用户活跃度 TOP10。
     *
     * @return 榜单项列表(按点唱数倒序,最多 10 项;无数据返回空表)
     */
    List<TopUserVO> topUsers();

    /**
     * 上传者贡献 TOP10。
     *
     * @return 榜单项列表(按总播放量倒序,最多 10 项)
     */
    List<TopUploaderVO> topUploaders();

    /**
     * 刷新两缓存(从 DB 重新聚合覆盖写回)。由定时任务调用。
     */
    void refreshAll();
}
