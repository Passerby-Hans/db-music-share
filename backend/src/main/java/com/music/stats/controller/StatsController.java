package com.music.stats.controller;

import com.music.common.annotation.RequireRole;
import com.music.common.result.Result;
import com.music.stats.dto.TopUploaderVO;
import com.music.stats.dto.TopUserVO;
import com.music.stats.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统计报表接口(管理后台)。
 *
 * <p>类级 {@link RequireRole}(2):仅管理员可访问。用户活跃度/上传者贡献 TOP10,
 * 走 Redis 缓存(cache-aside + 定时刷新)。播放量 TOP10 复用排行榜总榜
 * {@code GET /api/rank/total}(公开),不在本控制器。</p>
 */
@RestController
@RequestMapping("/api/admin/stats")
@RequireRole(2)
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * 用户活跃度 TOP10(按点唱次数)。
     *
     * @return 榜单项列表
     */
    @GetMapping("/top-users")
    public Result<List<TopUserVO>> topUsers() {
        return Result.success(statsService.topUsers());
    }

    /**
     * 上传者贡献 TOP10(按总播放量)。
     *
     * @return 榜单项列表
     */
    @GetMapping("/top-uploaders")
    public Result<List<TopUploaderVO>> topUploaders() {
        return Result.success(statsService.topUploaders());
    }
}
