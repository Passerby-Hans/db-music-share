package com.music.song.controller;

import com.music.common.annotation.RequireRole;
import com.music.common.result.PageVO;
import com.music.common.result.Result;
import com.music.song.dto.SongAuditDTO;
import com.music.song.dto.SongVO;
import com.music.song.service.SongService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 歌曲审核接口（管理后台）。
 *
 * <p>整类要求 role=2（管理员），由 {@link RequireRole} 在类级把关。
 * 与上传者自助的 {@code /api/song} 接口分离，语义上归属管理后台，
 * 风格与 {@code /api/admin/storage}（孤儿扫描）一致。</p>
 *
 * <p>审核仅作用于「待审」歌曲：通过后该歌进入口径A（公开可见、可播放），
 * 驳回则记录理由、仍不公开。已审核过的歌曲不可重复审核（409）。</p>
 */
@RestController
@RequestMapping("/api/admin/song")
@RequireRole(2)
public class SongAuditController {

    private final SongService songService;

    public SongAuditController(SongService songService) {
        this.songService = songService;
    }

    /**
     * 待审歌曲列表（audit_status=0 且未删），分页。
     *
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10
     * @return 分页待审歌曲列表
     */
    @GetMapping("/pending")
    public Result<PageVO<SongVO>> listPending(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(songService.listPending(page, size));
    }

    /**
     * 管理端歌曲全量列表(所有歌,含审核态 + 软删),支持筛选 + 分页。
     *
     * @param keyword     标题关键词,可空
     * @param auditStatus 审核态 0/1/2;空=全部
     * @param page        页码,默认 1
     * @param size        每页条数,默认 10
     * @return 分页歌曲列表(含 isDeleted)
     */
    @GetMapping("/all")
    public Result<PageVO<SongVO>> listAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(songService.listAllForAdmin(keyword, auditStatus, page, size));
    }

    /**
     * 审核一首待审歌曲：通过或驳回。
     *
     * @param sid 歌曲 sid
     * @param dto 审核参数（pass 必填；驳回时 remark 必填）
     * @return 成功响应
     */
    @PostMapping("/{sid}/audit")
    public Result<Void> audit(@PathVariable Long sid, @Valid @RequestBody SongAuditDTO dto) {
        songService.audit(sid, dto);
        return Result.success();
    }
}
