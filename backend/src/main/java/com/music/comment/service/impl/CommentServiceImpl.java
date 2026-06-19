package com.music.comment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.music.comment.dto.CommentCreateDTO;
import com.music.comment.dto.CommentReplyVO;
import com.music.comment.dto.CommentVO;
import com.music.comment.entity.Comment;
import com.music.comment.entity.CommentLike;
import com.music.comment.mapper.CommentLikeMapper;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final CommentLikeMapper commentLikeMapper;
    private final SongMapper songMapper;
    private final UserMapper userMapper;

    /**
     * 构造器注入依赖。
     *
     * @param commentMapper     评论数据访问
     * @param commentLikeMapper 评论点赞数据访问（点赞 CRUD + 批量统计/已赞集合）
     * @param songMapper        歌曲数据访问（发表评论前校验歌曲可见）
     * @param userMapper        用户数据访问（批量回填评论者昵称/头像）
     */
    public CommentServiceImpl(CommentMapper commentMapper,
                              CommentLikeMapper commentLikeMapper,
                              SongMapper songMapper,
                              UserMapper userMapper) {
        this.commentMapper = commentMapper;
        this.commentLikeMapper = commentLikeMapper;
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
        requireVisibleSong(dto.getSid());
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
     * 分页后批量回填回复数、评论者信息、点赞数与"我是否已赞"。
     *
     * <p>进入前先校验歌曲口径A可见：歌曲被改回待审/驳回/软删后，其评论
     * 应随歌曲一同"消失"（404），不能再被匿名枚举 sid 拉取，避免泄露已下架歌曲的评论内容。</p>
     */
    @Override
    public PageVO<CommentVO> listBySong(Long sid, Long currentUid, long page, long size) {
        // 歌曲不可见则视为不存在，连带其评论一并 404（与歌曲详情接口口径一致）
        requireVisibleSong(sid);
        var wrapper = Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getSid, sid)
                .isNull(Comment::getParentCid)
                .orderByDesc(Comment::getCid);
        IPage<Comment> result = commentMapper.selectPage(new Page<>(page, size), wrapper);
        List<Comment> records = result.getRecords();

        List<Long> cids = records.stream().map(Comment::getCid).toList();
        // 批量取本页主评论的回复数（按 parentCid 分组计数），避免逐条 selectCount
        Map<Long, Long> replyCountMap = countRepliesByParent(cids);
        // 批量取本页评论的点赞数（按 cid 分组计数）
        Map<Long, Long> likeCountMap = countLikesByComment(cids);
        // 批量取当前用户在本页评论里点过赞的 cid 集合（游客返回空集）
        Set<Long> likedSet = likedCidSet(currentUid, cids);
        // 批量取评论者用户信息（昵称/头像），避免逐条查 app_user
        Map<Long, User> userMap = loadUsers(records.stream().map(Comment::getUid).toList());

        List<CommentVO> vos = records.stream()
                .map(c -> toCommentVO(c, replyCountMap.getOrDefault(c.getCid(), 0L),
                        likeCountMap.getOrDefault(c.getCid(), 0L),
                        likedSet.contains(c.getCid()), userMap))
                .toList();
        return new PageVO<>(vos, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 某条主评论下的回复分页（parentCid = 入参），按 cid 升序（先回复在前，盖楼顺读）。
     * 每项回填点赞数与"我是否已赞"。
     *
     * <p>进入前先经父评论定位其所属歌曲并校验口径A可见：父评论不存在、
     * 或其歌曲已下架（待审/驳回/软删）时一律 404，防止绕过歌曲可见性直接按
     * parentCid 拉取已下架歌曲的回复。</p>
     */
    @Override
    public PageVO<CommentReplyVO> listReplies(Long parentCid, Long currentUid, long page, long size) {
        // 先定位父评论，拿到其所属歌曲；父评论不存在视为 404
        Comment parent = commentMapper.selectById(parentCid);
        if (parent == null) {
            throw new BizException(ResultCode.NOT_FOUND, "评论不存在");
        }
        // 父评论所属歌曲不可见则连带其回复一并 404
        requireVisibleSong(parent.getSid());
        var wrapper = Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getParentCid, parentCid)
                .orderByAsc(Comment::getCid);
        IPage<Comment> result = commentMapper.selectPage(new Page<>(page, size), wrapper);
        List<Comment> records = result.getRecords();

        List<Long> cids = records.stream().map(Comment::getCid).toList();
        Map<Long, Long> likeCountMap = countLikesByComment(cids);
        Set<Long> likedSet = likedCidSet(currentUid, cids);
        Map<Long, User> userMap = loadUsers(records.stream().map(Comment::getUid).toList());
        List<CommentReplyVO> vos = records.stream()
                .map(c -> toReplyVO(c, likeCountMap.getOrDefault(c.getCid(), 0L),
                        likedSet.contains(c.getCid()), userMap))
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
     * 点赞一条评论（幂等）。先校验评论存在，再查是否已赞，未赞才插入。
     * 不限制歌曲可见性：评论既已展示出来即可点赞。
     */
    @Override
    public void like(Long uid, Long cid) {
        Comment comment = commentMapper.selectById(cid);
        if (comment == null) {
            throw new BizException(ResultCode.NOT_FOUND, "评论不存在");
        }
        // 幂等：已点赞则直接返回，避免触发 (uid,cid) 唯一约束异常
        var exists = Wrappers.<CommentLike>lambdaQuery()
                .eq(CommentLike::getUid, uid)
                .eq(CommentLike::getCid, cid);
        if (commentLikeMapper.selectCount(exists) > 0) {
            return;
        }
        CommentLike like = new CommentLike();
        like.setUid(uid);
        like.setCid(cid);
        commentLikeMapper.insert(like);
    }

    /**
     * 取消点赞（幂等）。按 (uid,cid) 删除，无记录时影响 0 行亦视为成功。
     */
    @Override
    public void unlike(Long uid, Long cid) {
        var wrapper = Wrappers.<CommentLike>lambdaQuery()
                .eq(CommentLike::getUid, uid)
                .eq(CommentLike::getCid, cid);
        commentLikeMapper.delete(wrapper);
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
        // 点赞数与"我是否已赞"对主评论与回复都适用，故用全量 cids
        List<Long> cids = records.stream().map(Comment::getCid).toList();
        Map<Long, Long> likeCountMap = countLikesByComment(cids);
        Set<Long> likedSet = likedCidSet(uid, cids);
        Map<Long, User> userMap = loadUsers(records.stream().map(Comment::getUid).toList());

        List<CommentVO> vos = records.stream()
                .map(c -> toCommentVO(c, replyCountMap.getOrDefault(c.getCid(), 0L),
                        likeCountMap.getOrDefault(c.getCid(), 0L),
                        likedSet.contains(c.getCid()), userMap))
                .toList();
        // 回填所属歌名(我的评论可能跨多首歌,需一次性批量查歌名)
        Map<Long, String> titleMap = loadSongTitles(records.stream().map(Comment::getSid).toList());
        vos.forEach(vo -> vo.setSongTitle(titleMap.get(vo.getSid())));
        return new PageVO<>(vos, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 管理端评论全站列表:内容筛选 + 按 cid 倒序分页,回填 replyCount/likeCount/用户/歌名。
     *
     * <p>面向管理后台总览:不限歌曲可见性、不限登录用户身份,全站评论(主评论与回复)
     * 一并返回,按 cid 倒序(新在前)。复用 listMine 的批量回填套路(replyCount 仅对
     * 主评论统计、likeCount 与用户信息全量统计、歌名批量查),规避 N+1。
     * {@code likedByMe} 在管理端无意义(管理员不是评论互动方),恒填 false。</p>
     *
     * @param keyword 评论内容关键词,可空(空则不做内容筛选)
     * @param page    页码(从 1 起)
     * @param size    每页条数
     * @return 分页评论列表(含 sid + songTitle)
     */
    @Override
    public PageVO<CommentVO> listAllForAdmin(String keyword, long page, long size) {
        var wrapper = Wrappers.<Comment>lambdaQuery()
                .like(keyword != null && !keyword.isBlank(), Comment::getContent, keyword)
                .orderByDesc(Comment::getCid);
        IPage<Comment> result = commentMapper.selectPage(new Page<>(page, size), wrapper);
        List<Comment> records = result.getRecords();

        // 仅对其中的主评论(parentCid 为空)查回复数;回复项无下级无需计数
        List<Long> parentCids = records.stream()
                .filter(c -> c.getParentCid() == null)
                .map(Comment::getCid)
                .toList();
        Map<Long, Long> replyCountMap = countRepliesByParent(parentCids);
        // 点赞数对主评论与回复都适用,用全量 cids
        List<Long> cids = records.stream().map(Comment::getCid).toList();
        Map<Long, Long> likeCountMap = countLikesByComment(cids);
        Map<Long, User> userMap = loadUsers(records.stream().map(Comment::getUid).toList());
        Map<Long, String> titleMap = loadSongTitles(records.stream().map(Comment::getSid).toList());

        List<CommentVO> vos = records.stream()
                .map(c -> toCommentVO(c, replyCountMap.getOrDefault(c.getCid(), 0L),
                        likeCountMap.getOrDefault(c.getCid(), 0L),
                        false, userMap)) // likedByMe=false(管理端不关心互动态)
                .toList();
        vos.forEach(vo -> vo.setSongTitle(titleMap.get(vo.getSid())));
        return new PageVO<>(vos, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 校验歌曲处于口径A可见状态（已审核通过且未软删），否则抛 404。
     *
     * <p>评论的发表与读取都以此为前提：歌曲一旦下架（改回待审/被驳回/软删），
     * 其评论与回复应随之不可见，与歌曲详情接口的 404 口径保持一致。</p>
     *
     * @param sid 歌曲 sid
     * @throws BizException 歌曲不存在或不可见时抛 {@link ResultCode#NOT_FOUND}
     */
    private void requireVisibleSong(Long sid) {
        Song song = songMapper.selectById(sid);
        if (song == null
                || Boolean.TRUE.equals(song.getIsDeleted())
                || song.getAuditStatus() == null
                || song.getAuditStatus() != SONG_AUDIT_PASSED) {
            throw new BizException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
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
     * 按一批评论 cid 批量统计各自的点赞数（一次 group by 查询）。
     *
     * @param cids 评论 cid 列表，空列表直接返回空表（不发查询）
     * @return cid → 点赞数 的映射；无点赞者不出现在 map 中
     */
    private Map<Long, Long> countLikesByComment(List<Long> cids) {
        if (cids == null || cids.isEmpty()) {
            return Collections.emptyMap();
        }
        // 同 countRepliesByParent：用列原名 cid 与小写别名 cnt，避开 PG 别名折叠小写的坑
        QueryWrapper<CommentLike> q = new QueryWrapper<>();
        q.select("cid", "COUNT(*) AS cnt")
                .in("cid", cids)
                .groupBy("cid");
        List<Map<String, Object>> rows = commentLikeMapper.selectMaps(q);
        return rows.stream().collect(Collectors.toMap(
                r -> ((Number) r.get("cid")).longValue(),
                r -> ((Number) r.get("cnt")).longValue()));
    }

    /**
     * 取出当前用户在给定一批评论里点过赞的 cid 集合（一次查询）。
     *
     * <p>用于列表回填每项的 likedByMe 标志。游客（{@code currentUid == null}）
     * 或空评论集直接返回空集，不发查询。</p>
     *
     * @param currentUid 当前登录用户 uid；游客传 {@code null}
     * @param cids       本页评论 cid 列表
     * @return 当前用户已点赞的 cid 集合
     */
    private Set<Long> likedCidSet(Long currentUid, List<Long> cids) {
        if (currentUid == null || cids == null || cids.isEmpty()) {
            return Collections.emptySet();
        }
        var wrapper = Wrappers.<CommentLike>lambdaQuery()
                .select(CommentLike::getCid)
                .eq(CommentLike::getUid, currentUid)
                .in(CommentLike::getCid, cids);
        return commentLikeMapper.selectList(wrapper).stream()
                .map(CommentLike::getCid)
                .collect(Collectors.toCollection(HashSet::new));
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
     * 按一批 sid 批量加载歌曲标题(sid→title),供评论列表回填所属歌名。
     *
     * <p>管理端评论列表/我的评论跨多首歌,需一次性批量查歌名,避免逐条 N+1。
     * 注:歌曲软删(逻辑删,非物理删),{@code selectBatchIds} 仍能取到,
     * 故对软删歌的评论仍能正确回填 title(管理员需要看到下架歌的残留评论归属)。</p>
     *
     * @param sids 歌曲 sid 列表(可含重复,会自动去重)
     * @return sid → 歌曲标题 的映射;空列表返回空表
     */
    private Map<Long, String> loadSongTitles(List<Long> sids) {
        if (sids == null || sids.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = sids.stream().distinct().toList();
        return songMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(Song::getSid, Song::getTitle, (a, b) -> a));
    }

    /**
     * 评论实体转主评论 VO，回填回复数、点赞数、是否已赞与评论者昵称/头像。
     *
     * @param c          评论实体
     * @param replyCount 该评论的回复数
     * @param likeCount  该评论的实时点赞数
     * @param likedByMe  当前用户是否已点赞本条评论
     * @param userMap    uid→User 映射（用户可能已删，取不到则昵称/头像为空）
     * @return 主评论 VO
     */
    private CommentVO toCommentVO(Comment c, long replyCount, long likeCount,
                                  boolean likedByMe, Map<Long, User> userMap) {
        CommentVO vo = new CommentVO();
        vo.setCid(c.getCid());
        vo.setSid(c.getSid());
        vo.setUid(c.getUid());
        vo.setContent(c.getContent());
        vo.setLikeCount(likeCount);
        vo.setLikedByMe(likedByMe);
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
     * 评论实体转回复 VO，回填点赞数、是否已赞与回复者昵称/头像。
     *
     * @param c         评论实体（回复）
     * @param likeCount 该回复的实时点赞数
     * @param likedByMe 当前用户是否已点赞本条回复
     * @param userMap   uid→User 映射
     * @return 回复 VO
     */
    private CommentReplyVO toReplyVO(Comment c, long likeCount, boolean likedByMe, Map<Long, User> userMap) {
        CommentReplyVO vo = new CommentReplyVO();
        vo.setCid(c.getCid());
        vo.setParentCid(c.getParentCid());
        vo.setUid(c.getUid());
        vo.setContent(c.getContent());
        vo.setLikeCount(likeCount);
        vo.setLikedByMe(likedByMe);
        vo.setCreateTime(c.getCreateTime());
        User u = userMap.get(c.getUid());
        if (u != null) {
            vo.setNickname(u.getNickname());
            vo.setAvatar(u.getAvatar());
        }
        return vo;
    }
}
