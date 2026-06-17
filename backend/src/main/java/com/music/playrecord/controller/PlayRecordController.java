package com.music.playrecord.controller;

import com.music.common.context.UserContext;
import com.music.common.result.Result;
import com.music.playrecord.service.PlayRecordService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点唱记录接口。
 *
 * <p>仅一个写接口 {@code POST /api/play-record/{sid}},登录即可(无 @RequireRole,
 * 普通用户 role=0 亦可点唱)。前端在 {@code <audio>} 真正开始播放时调用。点唱是
 * 排行榜/统计的数据生产者;榜单读接口归后续排行榜阶段。</p>
 *
 * <p>身份取自 {@link UserContext}(服务端会话),不信任前端传入的 uid。</p>
 */
@RestController
@RequestMapping("/api/play-record")
public class PlayRecordController {

    private final PlayRecordService playRecordService;

    public PlayRecordController(PlayRecordService playRecordService) {
        this.playRecordService = playRecordService;
    }

    /**
     * 记录一次点唱(登录即可,幂等成功)。
     *
     * @param sid 被点唱的歌曲 sid
     * @return 成功响应
     */
    @PostMapping("/{sid}")
    public Result<Void> record(@PathVariable Long sid) {
        playRecordService.record(UserContext.getUid(), sid);
        return Result.success();
    }
}
