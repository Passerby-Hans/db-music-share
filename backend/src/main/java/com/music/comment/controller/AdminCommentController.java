package com.music.comment.controller;

import com.music.common.annotation.RequireRole;
import com.music.common.result.PageVO;
import com.music.common.result.Result;
import com.music.comment.dto.CommentVO;
import com.music.comment.service.CommentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论管理接口(管理后台)。
 *
 * <p>类级 {@link RequireRole}(2):仅管理员可访问。提供全站评论总览列表
 * (主评论 + 回复),支持内容关键词筛选 + 分页,每项回填所属歌曲标题 songTitle,
 * 便于管理员一眼定位评论归属。</p>
 *
 * <p><strong>强删评论复用 {@code DELETE /api/comment/{cid}}</strong>(管理员越权物理删
 * + DB 级联删其回复,已在 {@link CommentController} 实现,不在本控制器重复)。
 * 本控制器仅新增"列表"读接口,与既有删除能力组合成完整的评论管理闭环。</p>
 */
@RestController
@RequestMapping("/api/admin/comment")
@RequireRole(2)
public class AdminCommentController {

    /** 评论业务服务(查询全站评论列表 + 回填)。 */
    private final CommentService commentService;

    /**
     * 构造器注入评论业务服务。
     *
     * @param commentService 评论业务服务
     */
    public AdminCommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /**
     * 管理端全站评论列表(分页,含 sid + songTitle 回填)。
     *
     * <p>主评论与回复一并返回,按 cid 倒序(新在前)。likedByMe 恒为 false
     * (管理端非互动方)。回填 replyCount(仅主评论)/likeCount/评论者昵称头像/所属歌名。</p>
     *
     * @param keyword 评论内容关键词,可空(空则不做内容筛选)
     * @param page    页码,默认 1(从 1 起)
     * @param size    每页条数,默认 10
     * @return 分页评论列表(含 sid + songTitle)
     */
    @GetMapping
    public Result<PageVO<CommentVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(commentService.listAllForAdmin(keyword, page, size));
    }
}
