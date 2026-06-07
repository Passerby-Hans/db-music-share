package com.music.file.controller;

import com.music.common.annotation.RequireRole;
import com.music.common.result.Result;
import com.music.file.dto.OrphanScanResultVO;
import com.music.file.service.OrphanCleanupService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 存储管理接口(管理员)。
 *
 * <p>提供孤儿文件的手动扫描/清理入口；定时清理见
 * {@link com.music.file.task.OrphanCleanupTask}。</p>
 */
@RestController
@RequestMapping("/api/admin/storage")
@RequireRole(2)
public class StorageAdminController {

    private final OrphanCleanupService orphanCleanupService;

    public StorageAdminController(OrphanCleanupService orphanCleanupService) {
        this.orphanCleanupService = orphanCleanupService;
    }

    /**
     * 手动触发孤儿扫描。
     *
     * @param dryRun 默认 true(只扫不删，安全)；传 false 才真正删除
     * @return 扫描/清理结果
     */
    @PostMapping("/orphan-scan")
    public Result<OrphanScanResultVO> orphanScan(
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return Result.success(orphanCleanupService.scan(dryRun));
    }
}
