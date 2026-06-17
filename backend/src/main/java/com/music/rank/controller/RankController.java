package com.music.rank.controller;

import com.music.common.result.Result;
import com.music.rank.BoardType;
import com.music.rank.dto.RankItemVO;
import com.music.rank.service.RankService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 排行榜接口(公开)。
 *
 * <p>三个独立读榜接口,走 {@code WebMvcConfig} 白名单,无需登录。返回 Top10。
 * 榜单是公开展示、无个性化用户态,故纯白名单(非软鉴权)。</p>
 */
@RestController
@RequestMapping("/api/rank")
public class RankController {

    private final RankService rankService;

    public RankController(RankService rankService) {
        this.rankService = rankService;
    }

    /**
     * 总榜 Top10(全量累计播放数)。
     *
     * @return 榜单项列表
     */
    @GetMapping("/total")
    public Result<List<RankItemVO>> total() {
        return Result.success(rankService.list(BoardType.TOTAL));
    }

    /**
     * 日榜 Top10(当天播放数)。
     *
     * @return 榜单项列表
     */
    @GetMapping("/daily")
    public Result<List<RankItemVO>> daily() {
        return Result.success(rankService.list(BoardType.DAILY));
    }

    /**
     * 周榜 Top10(本周播放数)。
     *
     * @return 榜单项列表
     */
    @GetMapping("/weekly")
    public Result<List<RankItemVO>> weekly() {
        return Result.success(rankService.list(BoardType.WEEKLY));
    }
}
