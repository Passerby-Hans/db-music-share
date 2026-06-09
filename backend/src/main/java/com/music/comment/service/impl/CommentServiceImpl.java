package com.music.comment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.music.comment.dto.CommentCreateDTO;
import com.music.comment.dto.CommentReplyVO;
import com.music.comment.dto.CommentVO;
import com.music.comment.entity.Comment;
import com.music.comment.mapper.CommentMapper;
import com.music.comment.service.CommentService;
import com.music.common.exception.BizException;
import com.music.common.result.PageVO;
import com.music.common.result.ResultCode;
import com.music.song.entity.Song;
import com.music.song.mapper.SongMapper;
import com.music.user.entity.User;
import com.music.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 评论业务实现。
 *
 * <p>查询类接口的关键设计是<strong>规避 N+1</strong>：先分页查出本页评论，
 * 再一次性批量查出涉及的用户（昵称/头像）与回复数（按 parentCid 分组计数），
 * 在内存中拼装 VO；不逐条回查。这样每个列表接口固定 2~3 次 DB 访问，
 * 与页大小无关。</p>
 *
 * <p>权限与可见性：发表前校验歌曲口径A可见；删除做归属校验（本人或管理员）。
 * comment 表无软删，删除走物理删，主评论的回复由 DB 级联清理。</p>
 */
@Service
public class CommentServiceImpl implements CommentService {

    /** 角色：管理员（删除时越过归属校验，可删任意评论）。 */
    private static final int ROLE_ADMIN = 2;

    /** 歌曲审核状态：通过（口径A 可见条件之一，决定能否评论/展示评论）。 */
    private static final int SONG_AUDIT_PASSED = 1;

    private final CommentMapper commentMapper;
    private final SongMapper songMapper;
    private final UserMapper userMapper;

    /**
     * 构造器注入依赖。
     *
     * @param commentMapper 评论数据访问
     * @param songMapper    歌曲数据访问（发表评论前校验歌曲可见）
     * @param userMapper    用户数据访问（批量回填评论者昵称/头像）
     */
    public CommentServiceImpl(CommentMapper commentMapper, SongMapper songMapper, UserMapper userMapper) {
        this.commentMapper = commentMapper;
        this.songMapper = songMapper;
        this.userMapper = userMapper;
    }

    /**
     * 发表评论或回复。先校验歌曲口径A可见；回复场景再校验父评论合法
     * （存在、同一首歌、且本身是主评论），随后落库。
     */
    @Override
    public Long create(Long uid, CommentCreateDTO dto) {
        // 只能评论"公开可见"的歌曲（已审核且未删），否则视为不存在
        Song song = songMapper.selectById(dto.getSid());
        if (song == null
                || Boolean.TRUE.equals(song.getIsDeleted())
                || song.getAuditStatus() == null
                || song.getAuditStatus() != SONG_AUDIT_PASSED) {
            throw new BizException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        // 回复场景：校验父评论。两层盖楼——父必须是该歌下的"主评论"
        if (dto.getParentCid() != null) {
            Comment parent = commentMapper.selectById(dto.getParentCid());
            if (parent == null) {
                throw new BizException(ResultCode.NOT_FOUND, "要回复的评论不存在");
            }
            if (!parent.getSid().equals(dto.getSid())) {
                throw new BizException(ResultCode.BAD_REQUEST, "回复的评论不属于该歌曲");
            }
            // 父评论本身是回复(parentCid 非空)则拒绝：避免出现第三层，强制盖楼到主评论
            if (parent.getParentCid() != null) {
                throw new BizException(ResultCode.BAD_REQUEST, "仅支持两层评论，请回复主评论");
            }
        }
        Comment comment = new Comment();
        comment.setUid(uid);
        comment.setSid(dto.getSid());
        comment.setContent(dto.getContent());
        comment.setLikeCount(0);
        comment.setParentCid(dto.getParentCid());
        commentMapper.insert(comment);
        return comment.getCid();
    }

    /**
     * 某首歌的主评论分页（parentCid IS NULL），按 cid 倒序（等价时间倒序，新在前）。
     * 分页后批量回填回复数与评论者信息。
     */
    @Override
    public PageVO<CommentVO> listBySong(Long sid, long page, long size) {
        var wrapper = Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getSid, sid)
                .isNull(Comment::getParentCid)
                .orderByDesc(Comment::getCid);
        IPage<Comment> result = commentMapper.selectPage(new Page<>(page, size), wrapper);
        List<Comment> records = result.getRecords();

        // 批量取本页主评论的回复数（按 parentCid 分组计数），避免逐条 selectCount
        Map<Long, Long> replyCountMap = countRepliesByParent(
                records.stream().map(Comment::getCid).toList());
        // 批量取评论者用户信息（昵称/头像），避免逐条查 app_user
        Map<Long, User> userMap = loadUsers(records.stream().map(Comment::getUid).toList());

        List<CommentVO> vos = records.stream()
                .map(c -> toCommentVO(c, replyCountMap.getOrDefault(c.getCid(), 0L), userMap))
                .toList();
        return new PageVO<>(vos, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 某条主评论下的回复分页（parentCid = 入参），按 cid 升序（先回复在前，盖楼顺读）。
     */
    @Override
    public PageVO<CommentReplyVO> listReplies(Long parentCid, long page, long size) {
        var wrapper = Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getParentCid, parentCid)
                .orderByAsc(Comment::getCid);
        IPage<Comment> result = commentMapper.selectPage(new Page<>(page, size), wrapper);
        List<Comment> records = result.getRecords();

        Map<Long, User> userMap = loadUsers(records.stream().map(Comment::getUid).toList());
        List<CommentReplyVO> vos = records.stream()
                .map(c -> toReplyVO(c, userMap))
                .toList();
        return new PageVO<>(vos, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 删除评论：先取出校验归属（本人或管理员），再物理删除。
     * 主评论的回复由 DB 的 ON DELETE CASCADE 连带删除，本层不必递归。
     */
    @Override
    public void delete(Long operatorUid, Integer operatorRole, Long cid) {
        Comment comment = commentMapper.selectById(cid);
        if (comment == null) {
            throw new BizException(ResultCode.NOT_FOUND, "评论不存在");
        }
        // 非管理员只能删自己的评论
        if ((operatorRole == null || operatorRole != ROLE_ADMIN)
                && !comment.getUid().equals(operatorUid)) {
            throw new BizException(ResultCode.FORBIDDEN, "只能删除自己的评论");
        }
        commentMapper.deleteById(cid);
    }

    /**
     * 我的评论分页（主评论与回复都算），按 cid 倒序。复用 CommentVO：
     * 主评论回填真实回复数，回复项 replyCount 置 0（回复无下级）。
     */
    @Override
    public PageVO<CommentVO> listMine(Long uid, long page, long size) {
        var wrapper = Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getUid, uid)
                .orderByDesc(Comment::getCid);
        IPage<Comment> result = commentMapper.selectPage(new Page<>(page, size), wrapper);
        List<Comment> records = result.getRecords();

        // 仅对其中的主评论(parentCid 为空)查回复数；回复项无需计数
        List<Long> parentCids = records.stream()
                .filter(c -> c.getParentCid() == null)
                .map(Comment::getCid)
                .toList();
        Map<Long, Long> replyCountMap = countRepliesByParent(parentCids);
        Map<Long, User> userMap = loadUsers(records.stream().map(Comment::getUid).toList());

        List<CommentVO> vos = records.stream()
                .map(c -> toCommentVO(c, replyCountMap.getOrDefault(c.getCid(), 0L), userMap))
                .toList();
        return new PageVO<>(vos, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 按一批父评论 cid 批量统计各自的回复数（一次 group by 查询）。
     *
     * @param parentCids 主评论 cid 列表，空列表直接返回空表（不发查询）
     * @return parentCid → 回复数 的映射；无回复者不出现在 map 中
     */
    private Map<Long, Long> countRepliesByParent(List<Long> parentCids) {
        if (parentCids == null || parentCids.isEmpty()) {
            return Collections.emptyMap();
        }
        // 用原生 QueryWrapper 做投影 + 分组：SELECT parent_cid, COUNT(*) cnt ... GROUP BY parent_cid
        // 注意：PostgreSQL 会把未加引号的列别名折叠成小写，故 selectMaps 返回的 key
        // 是列原名/小写别名。这里不用驼峰别名，直接取列原名 parent_cid 与小写别名 cnt，
        // 避免 r.get("parentCid") 取不到值（曾导致 NPE）。
        QueryWrapper<Comment> q = new QueryWrapper<>();
        q.select("parent_cid", "COUNT(*) AS cnt")
                .in("parent_cid", parentCids)
                .groupBy("parent_cid");
        List<Map<String, Object>> rows = commentMapper.selectMaps(q);
        return rows.stream().collect(Collectors.toMap(
                r -> ((Number) r.get("parent_cid")).longValue(),
                r -> ((Number) r.get("cnt")).longValue()));
    }

    /**
     * 按一批 uid 批量加载用户，返回 uid→User 映射，供回填昵称/头像。
     *
     * @param uids 评论者 uid 列表（可含重复，会自动去重）
     * @return uid → User 的映射；空列表返回空表
     */
    private Map<Long, User> loadUsers(List<Long> uids) {
        if (uids == null || uids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> distinct = uids.stream().distinct().toList();
        List<User> users = userMapper.selectBatchIds(distinct);
        return users.stream().collect(Collectors.toMap(User::getUid, Function.identity()));
    }

    /**
     * 评论实体转主评论 VO，回填回复数与评论者昵称/头像。
     *
     * @param c          评论实体
     * @param replyCount 该评论的回复数
     * @param userMap    uid→User 映射（用户可能已删，取不到则昵称/头像为空）
     * @return 主评论 VO
     */
    private CommentVO toCommentVO(Comment c, long replyCount, Map<Long, User> userMap) {
        CommentVO vo = new CommentVO();
        vo.setCid(c.getCid());
        vo.setUid(c.getUid());
        vo.setContent(c.getContent());
        vo.setLikeCount(c.getLikeCount());
        vo.setReplyCount(replyCount);
        vo.setCreateTime(c.getCreateTime());
        User u = userMap.get(c.getUid());
        if (u != null) {
            vo.setNickname(u.getNickname());
            vo.setAvatar(u.getAvatar());
        }
        return vo;
    }

    /**
     * 评论实体转回复 VO，回填回复者昵称/头像。
     *
     * @param c       评论实体（回复）
     * @param userMap uid→User 映射
     * @return 回复 VO
     */
    private CommentReplyVO toReplyVO(Comment c, Map<Long, User> userMap) {
        CommentReplyVO vo = new CommentReplyVO();
        vo.setCid(c.getCid());
        vo.setParentCid(c.getParentCid());
        vo.setUid(c.getUid());
        vo.setContent(c.getContent());
        vo.setLikeCount(c.getLikeCount());
        vo.setCreateTime(c.getCreateTime());
        User u = userMap.get(c.getUid());
        if (u != null) {
            vo.setNickname(u.getNickname());
            vo.setAvatar(u.getAvatar());
        }
        return vo;
    }
}
