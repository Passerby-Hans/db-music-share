package com.music.comment.controller;

import com.music.comment.dto.CommentCreateDTO;
import com.music.comment.dto.CommentReplyVO;
import com.music.comment.dto.CommentVO;
import com.music.comment.service.CommentService;
import com.music.common.annotation.OptionalAuth;
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
 * <p>权限分三类：</p>
 * <ul>
 *   <li><b>可选登录（软鉴权）</b>：{@code GET /api/comment/song/{sid}} 与
 *       {@code GET /api/comment/{cid}/replies} 标注 {@link OptionalAuth}——
 *       游客可浏览评论区；登录用户则识别身份以回填每条评论的
 *       {@code likedByMe}（我是否点过赞）；带失效 token 会提示重新登录；</li>
 *   <li><b>需登录</b>：发表、删除、"我的评论"、点赞/取消点赞未标注
 *       {@link com.music.common.annotation.RequireRole}，即"登录即可"
 *       （拦截器仍校验会话有效性）。当前用户从 {@link UserContext} 取，
 *       不信任前端传入的 uid，杜绝伪造身份与越权删除；</li>
 *   <li><b>角色门槛</b>：本模块无——评论是 UGC，普通用户(role=0)即可发表/点赞。</li>
 * </ul>
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
    @OptionalAuth
    public Result<PageVO<CommentVO>> listBySong(
            @PathVariable Long sid,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        // 公开接口：游客 UserContext.getUid() 为 null，likedByMe 一律 false；
        // 登录用户则回填其真实点赞态
        return Result.success(commentService.listBySong(sid, UserContext.getUid(), page, size));
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
    @OptionalAuth
    public Result<PageVO<CommentReplyVO>> listReplies(
            @PathVariable Long cid,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(commentService.listReplies(cid, UserContext.getUid(), page, size));
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

    /**
     * 点赞一条评论（登录即可，幂等）。重复点赞不报错。
     *
     * @param cid 评论 cid
     * @return 成功响应
     */
    @PostMapping("/{cid}/like")
    public Result<Void> like(@PathVariable Long cid) {
        commentService.like(UserContext.getUid(), cid);
        return Result.success();
    }

    /**
     * 取消点赞（登录即可，幂等）。未点赞过也返回成功。
     *
     * @param cid 评论 cid
     * @return 成功响应
     */
    @DeleteMapping("/{cid}/like")
    public Result<Void> unlike(@PathVariable Long cid) {
        commentService.unlike(UserContext.getUid(), cid);
        return Result.success();
    }
}
