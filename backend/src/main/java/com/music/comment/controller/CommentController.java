package com.music.comment.controller;

import com.music.comment.dto.CommentCreateDTO;
import com.music.comment.dto.CommentReplyVO;
import com.music.comment.dto.CommentVO;
import com.music.comment.service.CommentService;
import com.music.common.context.UserContext;
import com.music.common.result.PageVO;
import com.music.common.result.Result;
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
 * 评论接口。
 *
 * <p>权限分两类：</p>
 * <ul>
 *   <li><b>公开查看</b>：{@code GET /api/comment/song/{sid}} 与
 *       {@code GET /api/comment/{cid}/replies} 无需登录，已在 WebMvcConfig
 *       的拦截器白名单中放行（游客也能浏览评论区）；</li>
 *   <li><b>需登录</b>：发表、删除、"我的评论"未标注 {@link com.music.common.annotation.RequireRole}，
 *       即"登录即可"（拦截器仍校验会话有效性）。当前用户从 {@link UserContext} 取，
 *       不信任前端传入的 uid，杜绝伪造身份与越权删除。</li>
 * </ul>
 *
 * <p>评论不分角色门槛：普通用户(role=0)即可发表，符合 UGC 场景。</p>
 */
@RestController
@RequestMapping("/api/comment")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /**
     * 发表评论或回复（登录即可）。
     *
     * @param dto 发表参数（sid、content、可选 parentCid）
     * @return 新建评论 cid
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody CommentCreateDTO dto) {
        return Result.success(commentService.create(UserContext.getUid(), dto));
    }

    /**
     * 某首歌的主评论分页（公开，新评论在前），每项带回复数与评论者信息。
     *
     * @param sid  歌曲 sid
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10
     * @return 分页主评论列表
     */
    @GetMapping("/song/{sid}")
    public Result<PageVO<CommentVO>> listBySong(
            @PathVariable Long sid,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(commentService.listBySong(sid, page, size));
    }

    /**
     * 某条主评论下的回复分页（公开，先回复在前）。
     *
     * @param cid  主评论 cid
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10
     * @return 分页回复列表
     */
    @GetMapping("/{cid}/replies")
    public Result<PageVO<CommentReplyVO>> listReplies(
            @PathVariable Long cid,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(commentService.listReplies(cid, page, size));
    }

    /**
     * 我的评论分页（登录即可），含主评论与回复，按时间倒序。
     *
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10
     * @return 分页评论列表
     */
    @GetMapping("/mine")
    public Result<PageVO<CommentVO>> listMine(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(commentService.listMine(UserContext.getUid(), page, size));
    }

    /**
     * 删除评论（登录即可；本人或管理员）。删主评论会级联删其下回复。
     *
     * @param cid 评论 cid
     * @return 成功响应
     */
    @DeleteMapping("/{cid}")
    public Result<Void> delete(@PathVariable Long cid) {
        var u = UserContext.get();
        commentService.delete(u.getUid(), u.getRole(), cid);
        return Result.success();
    }
}
