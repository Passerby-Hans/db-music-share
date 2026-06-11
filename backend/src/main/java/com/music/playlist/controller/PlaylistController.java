package com.music.playlist.controller;

import com.music.common.annotation.OptionalAuth;
import com.music.common.context.UserContext;
import com.music.common.result.PageVO;
import com.music.common.result.Result;
import com.music.common.session.LoginUser;
import com.music.playlist.dto.PlaylistCreateDTO;
import com.music.playlist.dto.PlaylistDetailVO;
import com.music.playlist.dto.PlaylistUpdateDTO;
import com.music.playlist.dto.PlaylistVO;
import com.music.playlist.service.PlaylistService;
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
 * 歌单接口。
 *
 * <p>权限分三类：</p>
 * <ul>
 *   <li><b>可选登录（软鉴权）</b>：{@code GET /api/playlist/{plid}} 详情标注
 *       {@link OptionalAuth}——游客可浏览公开歌单；登录用户额外能看到自己的私密歌单
 *       （Service 据当前身份判定可见性）；带失效 token 会提示重新登录；</li>
 *   <li><b>需登录</b>：创建、改、删、加歌、移歌、我的歌单未标注
 *       {@link com.music.common.annotation.RequireRole}，即"登录即可"
 *       （普通用户 role=0 亦可建歌单，符合 UGC 场景）。当前用户从
 *       {@link UserContext} 取，不信任前端传入的 uid，杜绝越权管理他人歌单；</li>
 *   <li><b>owner 门槛</b>：改/删/加歌/移歌在 Service 层做归属校验
 *       （owner 或管理员），非本人非管理员→403。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/playlist")
public class PlaylistController {

    private final PlaylistService playlistService;

    public PlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    /**
     * 创建歌单（登录即可）。
     *
     * @param dto 创建参数（playlistName 必填，isPublic 默认公开）
     * @return 新建歌单 plid
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PlaylistCreateDTO dto) {
        return Result.success(playlistService.create(UserContext.getUid(), dto));
    }

    /**
     * 修改歌单元信息（登录即可；owner 或管理员）。不涉及曲目增删。
     *
     * @param plid 歌单 plid
     * @param dto  更新参数（name、isPublic 必填）
     * @return 成功响应
     */
    @PutMapping("/{plid}")
    public Result<Void> update(@PathVariable Long plid,
                               @Valid @RequestBody PlaylistUpdateDTO dto) {
        LoginUser u = UserContext.get();
        playlistService.update(u.getUid(), u.getRole(), plid, dto);
        return Result.success();
    }

    /**
     * 删除歌单（登录即可；owner 或管理员）。曲目记录由 DB 级联清理。
     *
     * @param plid 歌单 plid
     * @return 成功响应
     */
    @DeleteMapping("/{plid}")
    public Result<Void> delete(@PathVariable Long plid) {
        LoginUser u = UserContext.get();
        playlistService.delete(u.getUid(), u.getRole(), plid);
        return Result.success();
    }

    /**
     * 我的歌单分页（登录即可，含私密），按创建时间倒序，每项带曲目数。
     *
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10
     * @return 歌单列表项分页
     */
    @GetMapping("/mine")
    public Result<PageVO<PlaylistVO>> listMine(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(playlistService.listMine(UserContext.getUid(), page, size));
    }

    /**
     * 公开歌单广场分页（登录即可）。<strong>1.0 暂为空壳</strong>，返回空分页占位，
     * 待后续「发现页」需求再补实现。
     *
     * @param keyword 名称关键词（可选，预留）
     * @param page    页码，默认 1
     * @param size    每页条数，默认 10
     * @return 公开歌单分页（当前恒为空）
     */
    @GetMapping("/public")
    public Result<PageVO<PlaylistVO>> listPublic(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(playlistService.listPublic(keyword, page, size));
    }

    /**
     * 歌单详情 + 曲目分页（软鉴权：游客看公开，登录额外看自己的私密）。
     * 私密歌单对无权访问者返回 404。
     *
     * @param plid 歌单 plid
     * @param page 曲目页码，默认 1
     * @param size 曲目每页条数，默认 20
     * @return 歌单详情（元信息 + 曲目分页）
     */
    @GetMapping("/{plid}")
    @OptionalAuth
    public Result<PlaylistDetailVO> getDetail(
            @PathVariable Long plid,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        // 软鉴权：游客 UserContext.get() 为 null；登录用户取其 uid/role 判定私密可见性
        LoginUser u = UserContext.get();
        Long uid = u == null ? null : u.getUid();
        Integer role = u == null ? null : u.getRole();
        return Result.success(playlistService.getDetail(plid, uid, role, page, size));
    }

    /**
     * 向歌单加歌（登录即可；owner 或管理员，幂等）。歌曲须公开可见。
     *
     * @param plid 歌单 plid
     * @param sid  歌曲 sid
     * @return 成功响应
     */
    @PostMapping("/{plid}/songs/{sid}")
    public Result<Void> addSong(@PathVariable Long plid, @PathVariable Long sid) {
        LoginUser u = UserContext.get();
        playlistService.addSong(u.getUid(), u.getRole(), plid, sid);
        return Result.success();
    }

    /**
     * 从歌单移歌（登录即可；owner 或管理员，幂等）。已下架的歌也可移出。
     *
     * @param plid 歌单 plid
     * @param sid  歌曲 sid
     * @return 成功响应
     */
    @DeleteMapping("/{plid}/songs/{sid}")
    public Result<Void> removeSong(@PathVariable Long plid, @PathVariable Long sid) {
        LoginUser u = UserContext.get();
        playlistService.removeSong(u.getUid(), u.getRole(), plid, sid);
        return Result.success();
    }
}
