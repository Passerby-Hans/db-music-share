package com.music.file.task;

import com.music.file.dto.OrphanScanResultVO;
import com.music.file.service.OrphanCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 孤儿文件定时清理任务。
 *
 * <p>按 {@code storage.minio.orphan-cron}(默认每天 04:30)执行真实清理(dryRun=false)，
 * 兜底回收"上传后未建歌引用"的悬空对象。手动入口见
 * {@link com.music.file.controller.StorageAdminController}。</p>
 */
@Component
public class OrphanCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(OrphanCleanupTask.class);

    private final OrphanCleanupService orphanCleanupService;

    public OrphanCleanupTask(OrphanCleanupService orphanCleanupService) {
        this.orphanCleanupService = orphanCleanupService;
    }

    /**
     * 定时执行孤儿清理(真实删除)。cron 取配置项 {@code storage.minio.orphan-cron}。
     */
    @Scheduled(cron = "${storage.minio.orphan-cron}")
    public void cleanup() {
        OrphanScanResultVO r = orphanCleanupService.scan(false);
        log.info("定时孤儿清理: 扫描audio={}/cover={}, 删除={}",
                r.getAudioScanned(), r.getCoverScanned(), r.getDeletedCount());
    }
}
