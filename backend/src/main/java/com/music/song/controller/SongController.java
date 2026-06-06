package com.music.song.controller;

import com.music.common.annotation.RequireRole;
import com.music.common.context.UserContext;
import com.music.common.result.PageVO;
import com.music.common.result.Result;
import com.music.song.dto.SongDetailVO;
import com.music.song.dto.SongMoveDTO;
import com.music.song.dto.SongUpdateDTO;
import com.music.song.dto.SongUploadDTO;
import com.music.song.dto.SongVO;
import com.music.song.service.SongService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 歌曲接口。
 *
 * <p>上传/修改/移动/删除要求 role≥1（上传者或管理员），由 {@link RequireRole}
 * 在方法级把关；公开查询与"我的上传"登录即可访问。当前用户身份从
 * {@link UserContext} 取，不信任前端传入的 uid，杜绝越权。</p>
 */
@RestController
@RequestMapping("/api/song")
public class SongController {

    private final SongService songService;

    public SongController(SongService songService) {
        this.songService = songService;
    }
    /**
     * 上传歌曲（待审）。专辑归属三选一见 {@link SongUploadDTO}。
     *
     * @param dto 上传参数
     * @return 新建歌曲 sid
     */
    @PostMapping
    @RequireRole({1, 2})
    public Result<Long> upload(@Valid @RequestBody SongUploadDTO dto) {
        return Result.success(songService.upload(UserContext.getUid(), dto));
    }

    /**
     * 公开歌曲列表（口径A：已审核 + 未删），可按标题搜索、分页。
     *
     * @param keyword 标题关键词，可空
     * @param page    页码，默认 1
     * @param size    每页条数，默认 10
     * @return 分页歌曲列表
     */
    @GetMapping("/public")
    public Result<PageVO<SongVO>> listPublic(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(songService.listPublic(keyword, page, size));
    }

    /**
     * 公开歌曲详情（口径A）。
     *
     * @param sid 歌曲 sid
     * @return 歌曲详情
     */
    @GetMapping("/public/{sid}")
    public Result<SongDetailVO> getPublic(@PathVariable Long sid) {
        return Result.success(songService.getPublic(sid));
    }

    /**
     * 我的上传（口径B：本人 + 未删，任意审核态），分页。
     *
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10
     * @return 分页歌曲列表
     */
    @GetMapping("/mine")
    public Result<PageVO<SongVO>> listMine(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(songService.listMine(UserContext.getUid(), page, size));
    }
    /**
     * 修改歌曲元信息（上传者本人改后回到待审，管理员改不重置）。
     *
     * @param sid 歌曲 sid
     * @param dto 修改参数
     * @return 成功响应
     */
    @PutMapping("/{sid}")
    @RequireRole({1, 2})
    public Result<Void> update(@PathVariable Long sid, @Valid @RequestBody SongUpdateDTO dto) {
        var u = UserContext.get();
        songService.update(u.getUid(), u.getRole(), sid, dto);
        return Result.success();
    }

    /**
     * 移动歌曲到另一专辑（含空缺省专辑自动清理）。
     *
     * @param sid 歌曲 sid
     * @param dto 目标专辑
     * @return 成功响应
     */
    @PutMapping("/{sid}/album")
    @RequireRole({1, 2})
    public Result<Void> move(@PathVariable Long sid, @Valid @RequestBody SongMoveDTO dto) {
        var u = UserContext.get();
        songService.move(u.getUid(), u.getRole(), sid, dto);
        return Result.success();
    }

    /**
     * 软删除歌曲。
     *
     * @param sid 歌曲 sid
     * @return 成功响应
     */
    @DeleteMapping("/{sid}")
    @RequireRole({1, 2})
    public Result<Void> delete(@PathVariable Long sid) {
        var u = UserContext.get();
        songService.delete(u.getUid(), u.getRole(), sid);
        return Result.success();
    }
}
