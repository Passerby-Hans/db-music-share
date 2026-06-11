package com.music.rating.controller;

import com.music.common.annotation.OptionalAuth;
import com.music.common.context.UserContext;
import com.music.common.result.PageVO;
import com.music.common.result.Result;
import com.music.rating.dto.MyRatingVO;
import com.music.rating.dto.RatingDTO;
import com.music.rating.dto.RatingStatVO;
import com.music.rating.service.RatingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评分接口。
 *
 * <p>权限分两类：</p>
 * <ul>
 *   <li><b>可选登录（软鉴权）</b>：{@code GET /api/rating/{sid}} 评分概况标注
 *       {@link OptionalAuth}——游客可看某歌的平均分与人数（myScore=null）；
 *       登录用户额外回填「我打了几分」；带失效 token 会提示重新登录；</li>
 *   <li><b>需登录</b>：提交评分、撤销评分、「我的评分」未标注
 *       {@link com.music.common.annotation.RequireRole}，即"登录即可"
 *       （普通用户 role=0 亦可评分，符合 UGC 场景）。当前用户从
 *       {@link UserContext} 取，不信任前端传入的 uid，杜绝替他人评分/撤分。</li>
 * </ul>
 *
 * <p>「提交评分」为 upsert 语义：首次评分插入、再次评分改分，对前端是单一接口。</p>
 */
@RestController
@RequestMapping("/api/rating")
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    /**
     * 提交/更新我对某歌的评分（登录即可，upsert）。仅能给公开可见的歌评分。
     *
     * @param sid 歌曲 sid
     * @param dto 评分参数（score 1~5，必填）
     * @return 成功响应
     */
    @PostMapping("/{sid}")
    public Result<Void> rate(@PathVariable Long sid, @Valid @RequestBody RatingDTO dto) {
        ratingService.rate(UserContext.getUid(), sid, dto.getScore());
        return Result.success();
    }

    /**
     * 撤销我对某歌的评分（登录即可，幂等）。已下架的歌也允许撤销。
     *
     * @param sid 歌曲 sid
     * @return 成功响应
     */
    @DeleteMapping("/{sid}")
    public Result<Void> cancel(@PathVariable Long sid) {
        ratingService.cancel(UserContext.getUid(), sid);
        return Result.success();
    }

    /**
     * 某歌评分概况（软鉴权）：平均分 + 评分人数 + 我的评分。
     * 游客 myScore 为 null；登录用户回填本人评分。
     *
     * @param sid 歌曲 sid
     * @return 评分概况
     */
    @GetMapping("/{sid}")
    @OptionalAuth
    public Result<RatingStatVO> getStat(@PathVariable Long sid) {
        // 软鉴权：游客 UserContext.getUid() 为 null → myScore=null；登录用户回填本人评分
        return Result.success(ratingService.getStat(sid, UserContext.getUid()));
    }

    /**
     * 我的评分分页（登录即可），按评分时间倒序，每项带分数与 playable 标志。
     * 失效歌曲不剔除，仅标记不可播放，由前端置灰。
     *
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10
     * @return 我的评分列表项分页
     */
    @GetMapping("/mine")
    public Result<PageVO<MyRatingVO>> listMine(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(ratingService.listMine(UserContext.getUid(), page, size));
    }
}
