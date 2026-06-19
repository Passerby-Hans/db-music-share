package com.music.song.controller;

import com.music.common.annotation.RequireRole;
import com.music.common.context.UserContext;
import com.music.common.result.PageVO;
import com.music.common.result.Result;
import com.music.song.dto.AlbumCreateDTO;
import com.music.song.dto.AlbumDetailVO;
import com.music.song.dto.AlbumUpdateDTO;
import com.music.song.dto.AlbumVO;
import com.music.song.service.AlbumService;
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
 * 专辑接口。
 *
 * <p>新建/修改/删除要求 role≥1（上传者或管理员），由 {@link RequireRole}
 * 把关；公开查询与"我的专辑"登录即可。归属与缺省专辑等业务校验在 Service 层。</p>
 */
@RestController
@RequestMapping("/api/album")
public class AlbumController {

    private final AlbumService albumService;

    public AlbumController(AlbumService albumService) {
        this.albumService = albumService;
    }
    /**
     * 新建普通专辑。
     *
     * @param dto 专辑参数
     * @return 新建专辑 aid
     */
    @PostMapping
    @RequireRole({1, 2})
    public Result<Long> create(@Valid @RequestBody AlbumCreateDTO dto) {
        return Result.success(albumService.create(UserContext.getUid(), dto));
    }

    /**
     * 公开专辑列表（未删），可按名搜索、分页。
     *
     * @param keyword 专辑名关键词，可空
     * @param page    页码，默认 1
     * @param size    每页条数，默认 10
     * @return 分页专辑列表
     */
    @GetMapping("/public")
    public Result<PageVO<AlbumVO>> listPublic(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(albumService.listPublic(keyword, sort, page, size));
    }

    /**
     * 公开专辑详情（未删）+ 其下口径A可见歌曲列表。
     *
     * @param aid 专辑 aid
     * @return 专辑详情（含可见曲目）
     */
    @GetMapping("/public/{aid}")
    public Result<AlbumDetailVO> getPublic(@PathVariable Long aid) {
        AlbumVO album = albumService.getPublic(aid);
        return Result.success(new AlbumDetailVO(album, albumService.listPublicSongs(aid)));
    }

    /**
     * 我创建的专辑（本人 + 未删），分页。
     *
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10
     * @return 分页专辑列表
     */
    @GetMapping("/mine")
    public Result<PageVO<AlbumVO>> listMine(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(albumService.listMine(UserContext.getUid(), page, size));
    }
    /**
     * 修改专辑信息（缺省专辑禁止修改，由 Service 层拦截）。
     *
     * @param aid 专辑 aid
     * @param dto 修改参数
     * @return 成功响应
     */
    @PutMapping("/{aid}")
    @RequireRole({1, 2})
    public Result<Void> update(@PathVariable Long aid, @Valid @RequestBody AlbumUpdateDTO dto) {
        var u = UserContext.get();
        albumService.update(u.getUid(), u.getRole(), aid, dto);
        return Result.success();
    }

    /**
     * 删除专辑（级联软删：先删专辑内所有歌，再删专辑）。
     *
     * @param aid 专辑 aid
     * @return 成功响应
     */
    @DeleteMapping("/{aid}")
    @RequireRole({1, 2})
    public Result<Void> delete(@PathVariable Long aid) {
        var u = UserContext.get();
        albumService.delete(u.getUid(), u.getRole(), aid);
        return Result.success();
    }
}
