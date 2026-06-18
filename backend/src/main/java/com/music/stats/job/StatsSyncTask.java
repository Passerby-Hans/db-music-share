package com.music.stats.job;

import com.music.stats.service.StatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 统计缓存定时刷新任务。
 *
 * <p>两类触发:</p>
 * <ul>
 *   <li>{@link #hourly()}:每小时整点刷新两缓存,保新鲜(数据最多滞后 1h);</li>
 *   <li>{@link #onStartup()}:应用启动完成时({@link ApplicationReadyEvent})刷新一次,
 *       兜底 Redis 重启后缓存为空——否则要等首个管理员读触发 cache-aside 聚合。</li>
 * </ul>
 *
 * <p>刷新失败只记 error 不阻断。{@code @EnableScheduling} 已由孤儿清理 + 排行榜对账任务开启。</p>
 */
@Component
public class StatsSyncTask {

    private static final Logger log = LoggerFactory.getLogger(StatsSyncTask.class);

    private final StatsService statsService;

    public StatsSyncTask(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * 每小时整点刷新两统计缓存。cron = 秒 分 时 日 月 周。
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void hourly() {
        statsService.refreshAll();
        log.info("统计缓存刷新完成");
    }

    /**
     * 应用启动完成后刷新一次(兜底 Redis 重启丢失)。
     * 同步执行,课设数据量耗时可忽略;数据量大时考虑 @Async。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        statsService.refreshAll();
    }
}
