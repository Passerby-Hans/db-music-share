package com.music.comment.service;

import com.music.comment.dto.CommentCreateDTO;
import com.music.comment.dto.CommentReplyVO;
import com.music.comment.dto.CommentVO;
import com.music.common.result.PageVO;

/**
 * 评论业务接口。
 *
 * <p>承载评论的发表、按歌曲查看主评论、查看某主评论的回复、删除、以及
 * "我的评论"。设计要点：</p>
 * <ul>
 *   <li><b>两层盖楼</b>：仅主评论 + 回复两层，回复再回复仍挂同一主评论；</li>
 *   <li><b>免审核</b>：评论发表即可见（UGC 文本，不同于涉版权的歌曲文件）；</li>
 *   <li><b>物理删除 + 级联</b>：comment 表无软删，删除即物理删，删主评论
 *       由 DB 的 ON DELETE CASCADE 连带删除其全部回复；</li>
 *   <li><b>权限</b>：发表/删除/我的 需登录（Controller 不加 RequireRole 即"登录即可"）；
 *       删除仅本人或管理员；查看类接口公开（WebMvcConfig 白名单放行）。</li>
 * </ul>
 */
public interface CommentService {

    /**
     * 发表评论或回复。
     *
     * <p>校验歌曲须公开可见（口径A：已审核且未删）；若为回复，parentCid 须存在、
     * 属于同一首歌、且本身是主评论（不允许对回复再开新层，盖楼到主评论下）。</p>
     *
     * @param uid 评论者 uid（当前登录用户）
     * @param dto 发表参数（sid、content、可选 parentCid）
     * @return 新建评论的 cid
     */
    Long create(Long uid, CommentCreateDTO dto);

    /**
     * 某首歌的主评论分页（parentCid 为空者），按时间倒序（新评论在前）。
     * 每项带回复数与评论者昵称/头像。
     *
     * @param sid  歌曲 sid
     * @param page 页码（从 1 起）
     * @param size 每页条数
     * @return 分页主评论列表
     */
    PageVO<CommentVO> listBySong(Long sid, long page, long size);

    /**
     * 某条主评论下的回复分页，按时间正序（先回复在前，符合盖楼阅读习惯）。
     *
     * @param parentCid 主评论 cid
     * @param page      页码（从 1 起）
     * @param size      每页条数
     * @return 分页回复列表
     */
    PageVO<CommentReplyVO> listReplies(Long parentCid, long page, long size);

    /**
     * 删除评论：仅作者本人或管理员可删。删主评论时其下回复由 DB 级联删除。
     *
     * @param operatorUid  操作者 uid
     * @param operatorRole 操作者角色（2=管理员，越过归属校验）
     * @param cid          待删评论 cid
     */
    void delete(Long operatorUid, Integer operatorRole, Long cid);

    /**
     * 我的评论分页（含主评论与回复），按时间倒序，供用户管理自己的评论。
     *
     * @param uid  当前用户 uid
     * @param page 页码（从 1 起）
     * @param size 每页条数
     * @return 分页评论列表（复用 CommentVO，回复项 replyCount 恒为 0）
     */
    PageVO<CommentVO> listMine(Long uid, long page, long size);
}
