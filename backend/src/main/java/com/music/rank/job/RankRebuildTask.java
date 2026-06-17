package com.music.rank.job;

import com.music.rank.BoardType;
import com.music.rank.service.RankService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 排行榜对账定时任务。
 *
 * <p>两类触发:</p>
 * <ul>
 *   <li>{@link #daily()}:每日凌晨 3:00 全量重建三榜,消除累积漂移(设计文档 §7.4);</li>
 *   <li>{@link #onStartup()}:应用启动完成时({@link ApplicationReadyEvent})重建一次,
 *       兜底 Redis 重启丢失——否则要等到次日凌晨榜单才恢复。</li>
 * </ul>
 *
 * <p>对账失败只记 error 不阻断(尤其启动对账,不能拖垮应用就绪)。{@code @EnableScheduling}
 * 已由孤儿清理任务({@code OrphanCleanupTask})开启。</p>
 */
@Component
public class RankRebuildTask {

    private static final Logger log = LoggerFactory.getLogger(RankRebuildTask.class);

    private final RankService rankService;

    public RankRebuildTask(RankService rankService) {
        this.rankService = rankService;
    }

    /**
     * 每日凌晨 3:00 全量对账三榜。
     * cron = 秒 分 时 日 月 周(凌晨 3 点)。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void daily() {
        rebuildAll();
    }

    /**
     * 应用启动完成后重建一次(兜底 Redis 重启丢失)。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        rebuildAll();
    }

    /**
     * 逐榜重建,单榜失败不影响其它。
     */
    public void rebuildAll() {
        for (BoardType board : BoardType.values()) {
            try {
                rankService.rebuild(board);
                log.info("排行榜对账完成: {}", board);
            } catch (Exception e) {
                log.error("排行榜对账失败: {}", board, e);
            }
        }
    }
}
