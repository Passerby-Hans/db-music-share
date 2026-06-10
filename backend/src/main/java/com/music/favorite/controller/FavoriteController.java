package com.music.favorite.controller;

import com.music.common.context.UserContext;
import com.music.common.result.PageVO;
import com.music.common.result.Result;
import com.music.favorite.dto.FavoriteVO;
import com.music.favorite.service.FavoriteService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收藏接口。
 *
 * <p>全部接口<strong>需登录</strong>（未标注 {@link com.music.common.annotation.RequireRole}
 * 即"登录即可"，普通用户 role=0 亦可，符合 UGC 场景；拦截器仍校验会话有效性）。
 * 当前用户一律从 {@link UserContext} 取，不接收前端传入的 uid，
 * 杜绝伪造身份收藏/取消他人收藏。</p>
 *
 * <p>收藏与取消均设计为<strong>幂等</strong>：重复收藏不报错、取消未收藏的歌也成功，
 * 前端无需先查状态再操作。</p>
 */
@RestController
@RequestMapping("/api/favorite")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    /**
     * 收藏一首歌（登录即可，幂等）。仅能收藏公开可见的歌。
     *
     * @param sid 歌曲 sid
     * @return 成功响应
     */
    @PostMapping("/{sid}")
    public Result<Void> add(@PathVariable Long sid) {
        favoriteService.add(UserContext.getUid(), sid);
        return Result.success();
    }

    /**
     * 取消收藏（登录即可，幂等）。已下架的歌也允许取消。
     *
     * @param sid 歌曲 sid
     * @return 成功响应
     */
    @DeleteMapping("/{sid}")
    public Result<Void> remove(@PathVariable Long sid) {
        favoriteService.remove(UserContext.getUid(), sid);
        return Result.success();
    }

    /**
     * 我的收藏分页（登录即可），按收藏时间倒序，每项带 playable 标志。
     * 失效歌曲不剔除，仅标记不可播放，由前端置灰。
     *
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10
     * @return 收藏列表项分页
     */
    @GetMapping("/mine")
    public Result<PageVO<FavoriteVO>> listMine(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(favoriteService.listMine(UserContext.getUid(), page, size));
    }

    /**
     * 查询我是否已收藏某首歌（登录即可），供详情页渲染收藏按钮状态。
     *
     * @param sid 歌曲 sid
     * @return 已收藏返回 true，否则 false
     */
    @GetMapping("/{sid}/status")
    public Result<Boolean> status(@PathVariable Long sid) {
        return Result.success(favoriteService.isFavorited(UserContext.getUid(), sid));
    }
}
