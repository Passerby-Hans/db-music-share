package com.music.playlist.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.music.common.exception.BizException;
import com.music.common.result.PageVO;
import com.music.common.result.ResultCode;
import com.music.playlist.dto.PlaylistCreateDTO;
import com.music.playlist.dto.PlaylistDetailVO;
import com.music.playlist.dto.PlaylistSongVO;
import com.music.playlist.dto.PlaylistUpdateDTO;
import com.music.playlist.dto.PlaylistVO;
import com.music.playlist.entity.Playlist;
import com.music.playlist.entity.PlaylistDetail;
import com.music.playlist.mapper.PlaylistDetailMapper;
import com.music.playlist.mapper.PlaylistMapper;
import com.music.playlist.service.PlaylistService;
import com.music.song.entity.Song;
import com.music.song.mapper.SongMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 歌单业务实现。
 *
 * <p>歌单 CRUD 为 {@code playlist} 单表操作；加歌/移歌为 {@code playlist_detail}
 * 单表操作，依赖 {@code (plid,sid)} 唯一约束实现幂等。两类列表与详情为防 N+1：
 * 先分页查主记录，再批量查关联数据（曲目数 group by 统计、歌曲信息 batchIds 回填）
 * 在内存拼装，固定查询次数与页大小无关——同收藏/评论模块的实现思路。</p>
 *
 * <p>可见性与权限：歌单层（公开任意可见 / 私密仅 owner+管理员）+ 歌曲层（口径A）
 * 两层叠加；写操作做归属校验（owner 或管理员）。详见 {@link PlaylistService}。</p>
 */
@Service
public class PlaylistServiceImpl implements PlaylistService {

    /** 角色：管理员（越过归属校验，可管理任意歌单）。 */
    private static final int ROLE_ADMIN = 2;

    /** 歌曲审核状态：通过（口径A 可见条件之一，决定能否被加入歌单）。 */
    private static final int SONG_AUDIT_PASSED = 1;

    private final PlaylistMapper playlistMapper;
    private final PlaylistDetailMapper playlistDetailMapper;
    private final SongMapper songMapper;

    /**
     * 构造器注入依赖。
     *
     * @param playlistMapper       歌单数据访问
     * @param playlistDetailMapper 歌单详情（曲目关系）数据访问
     * @param songMapper           歌曲数据访问（加歌校验可见、详情回填歌曲信息）
     */
    public PlaylistServiceImpl(PlaylistMapper playlistMapper,
                               PlaylistDetailMapper playlistDetailMapper,
                               SongMapper songMapper) {
        this.playlistMapper = playlistMapper;
        this.playlistDetailMapper = playlistDetailMapper;
        this.songMapper = songMapper;
    }

    /**
     * 创建歌单。归属当前用户，isPublic 为空兜底为公开。
     */
    @Override
    public Long create(Long uid, PlaylistCreateDTO dto) {
        Playlist p = new Playlist();
        p.setUid(uid);
        p.setPlaylistName(dto.getPlaylistName());
        p.setDescription(dto.getDescription());
        p.setCover(dto.getCover());
        // isPublic 为 null 时兜底为公开（true），对齐数据库 DEFAULT TRUE
        p.setIsPublic(dto.getIsPublic() == null ? Boolean.TRUE : dto.getIsPublic());
        // create_time 由数据库 DEFAULT CURRENT_TIMESTAMP 自动填充，不在此设置
        playlistMapper.insert(p);
        return p.getPlid();
    }

    /**
     * 修改歌单元信息。先取歌单做存在性+归属校验，再更新可改字段。
     */
    @Override
    public void update(Long uid, Integer role, Long plid, PlaylistUpdateDTO dto) {
        Playlist p = requireOwned(plid, uid, role);
        p.setPlaylistName(dto.getPlaylistName());
        p.setDescription(dto.getDescription());
        p.setCover(dto.getCover());
        p.setIsPublic(dto.getIsPublic());
        playlistMapper.updateById(p);
    }

    /**
     * 删除歌单。存在性+归属校验后物理删除；曲目记录由 DB 级联清理。
     */
    @Override
    public void delete(Long uid, Integer role, Long plid) {
        requireOwned(plid, uid, role);
        playlistMapper.deleteById(plid);
    }

    /**
     * 取歌单并校验「存在」且「当前用户有管理权（owner 或管理员）」。
     *
     * <p>不存在→404；存在但既非 owner 又非管理员→403。改/删/加歌/移歌
     * 四个写操作共用本校验，集中归属逻辑。</p>
     *
     * @param plid 歌单 plid
     * @param uid  操作者 uid
     * @param role 操作者角色
     * @return 校验通过的歌单实体
     * @throws BizException 不存在→404；无权→403
     */
    private Playlist requireOwned(Long plid, Long uid, Integer role) {
        Playlist p = playlistMapper.selectById(plid);
        if (p == null) {
            throw new BizException(ResultCode.NOT_FOUND, "歌单不存在");
        }
        boolean isAdmin = role != null && role == ROLE_ADMIN;
        boolean isOwner = p.getUid().equals(uid);
        if (!isAdmin && !isOwner) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作他人歌单");
        }
        return p;
    }

    /**
     * 我的歌单分页（含私密），按创建时间倒序。分页查歌单后，
     * 用一次 group by 批量统计本页各歌单的曲目数回填，规避 N+1。
     */
    @Override
    public PageVO<PlaylistVO> listMine(Long uid, long page, long size) {
        LambdaQueryWrapper<Playlist> wrapper = Wrappers.<Playlist>lambdaQuery()
                .eq(Playlist::getUid, uid)
                .orderByDesc(Playlist::getCreateTime);
        IPage<Playlist> result = playlistMapper.selectPage(new Page<>(page, size), wrapper);
        List<Playlist> records = result.getRecords();

        // 批量统计本页歌单的曲目数：plid → count
        Map<Long, Long> countMap = countSongsByPlaylist(
                records.stream().map(Playlist::getPlid).toList());

        List<PlaylistVO> vos = records.stream()
                .map(p -> PlaylistVO.from(p, countMap.getOrDefault(p.getPlid(), 0L)))
                .toList();
        return new PageVO<>(vos, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 公开歌单广场分页（占位空壳）。
     *
     * <p>1.0 暂不实现，返回空分页占位，保留接口契约。后续补实现时：
     * {@code is_public=true} 过滤 + 可选 keyword 模糊匹配 playlist_name +
     * 批量回填曲目数（复用 {@link #countSongsByPlaylist}）。</p>
     */
    @Override
    public PageVO<PlaylistVO> listPublic(String keyword, long page, long size) {
        // TODO 公开歌单广场：1.0 暂为空壳。后续按 is_public=true + keyword 实现"发现页"。
        return new PageVO<>(Collections.emptyList(), 0L, page, size);
    }

    /**
     * 按一批 plid 批量统计各歌单的曲目数，返回 plid→count 映射。
     *
     * <p>用 {@code SELECT plid, COUNT(*) ... GROUP BY plid} 一次查出，避免逐个歌单
     * 查 count 的 N+1。<strong>注意</strong>：SELECT 列与 GROUP BY 用列原名 {@code plid}
     * （非驼峰别名）——PostgreSQL 会把未加引号的别名折叠为小写，用驼峰 key 从
     * selectMaps 的 Map 中取值会落空（评论模块曾踩此坑）。</p>
     *
     * @param plids 歌单 plid 列表（可空）
     * @return plid → 曲目数；无曲目的歌单不在 map 中，调用方用 getOrDefault(.,0L) 兜底
     */
    private Map<Long, Long> countSongsByPlaylist(List<Long> plids) {
        if (plids == null || plids.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<PlaylistDetail> wrapper = new QueryWrapper<>();
        wrapper.select("plid", "COUNT(*) AS cnt")
                .in("plid", plids)
                .groupBy("plid");
        List<Map<String, Object>> rows = playlistDetailMapper.selectMaps(wrapper);
        return rows.stream().collect(Collectors.toMap(
                row -> ((Number) row.get("plid")).longValue(),
                row -> ((Number) row.get("cnt")).longValue()));
    }

    /**
     * 歌单详情 + 曲目分页。先做可见性校验（私密仅 owner/管理员），
     * 再分页查曲目记录并批量回填歌曲信息算 playable。
     */
    @Override
    public PlaylistDetailVO getDetail(Long plid, Long currentUid, Integer currentRole,
                                      long page, long size) {
        Playlist p = playlistMapper.selectById(plid);
        if (p == null) {
            throw new BizException(ResultCode.NOT_FOUND, "歌单不存在");
        }
        // 可见性：私密歌单仅 owner 或管理员可看；对其他人（含游客）一律 404，
        // 不泄露"该私密歌单存在"这一事实
        if (!Boolean.TRUE.equals(p.getIsPublic())) {
            boolean isAdmin = currentRole != null && currentRole == ROLE_ADMIN;
            boolean isOwner = currentUid != null && p.getUid().equals(currentUid);
            if (!isAdmin && !isOwner) {
                throw new BizException(ResultCode.NOT_FOUND, "歌单不存在");
            }
        }

        // 曲目总数（用于 VO 的 songCount 与分页 total 自洽）
        long total = playlistDetailMapper.selectCount(Wrappers.<PlaylistDetail>lambdaQuery()
                .eq(PlaylistDetail::getPlid, plid));

        // 分页查本页曲目记录（按加入时间倒序），再批量回填歌曲信息
        LambdaQueryWrapper<PlaylistDetail> wrapper = Wrappers.<PlaylistDetail>lambdaQuery()
                .eq(PlaylistDetail::getPlid, plid)
                .orderByDesc(PlaylistDetail::getAddTime);
        IPage<PlaylistDetail> detailPage = playlistDetailMapper.selectPage(new Page<>(page, size), wrapper);
        List<PlaylistDetail> details = detailPage.getRecords();

        Map<Long, Song> songMap = loadSongs(details.stream().map(PlaylistDetail::getSid).toList());
        List<PlaylistSongVO> songVOs = details.stream()
                // 理论上歌曲必存在（外键 + CASCADE）；防御性跳过被物理清理的脏数据
                .filter(d -> songMap.containsKey(d.getSid()))
                .map(d -> PlaylistSongVO.from(songMap.get(d.getSid()), d.getAddTime()))
                .toList();

        PlaylistVO meta = PlaylistVO.from(p, total);
        PageVO<PlaylistSongVO> songs =
                new PageVO<>(songVOs, total, detailPage.getCurrent(), detailPage.getSize());
        return new PlaylistDetailVO(meta, songs);
    }

    /**
     * 向歌单加歌（幂等）。归属校验 + 歌曲口径A可见校验后，未收录才插入。
     */
    @Override
    public void addSong(Long uid, Integer role, Long plid, Long sid) {
        // 校验歌单存在且当前用户有管理权
        requireOwned(plid, uid, role);
        // 只能加入"公开可见"的歌（已审核且未删），否则视为不存在
        Song song = songMapper.selectById(sid);
        if (song == null
                || Boolean.TRUE.equals(song.getIsDeleted())
                || song.getAuditStatus() == null
                || song.getAuditStatus() != SONG_AUDIT_PASSED) {
            throw new BizException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        // 幂等：已在歌单中则直接返回，避免触发 (plid,sid) 唯一约束异常
        if (existsSong(plid, sid)) {
            return;
        }
        PlaylistDetail detail = new PlaylistDetail();
        detail.setPlid(plid);
        detail.setSid(sid);
        playlistDetailMapper.insert(detail);
    }

    /**
     * 从歌单移歌（幂等）。归属校验后按 (plid,sid) 删除，无记录亦视为成功。
     * 不校验歌曲可见性：已下架的歌也允许移出。
     */
    @Override
    public void removeSong(Long uid, Integer role, Long plid, Long sid) {
        requireOwned(plid, uid, role);
        playlistDetailMapper.delete(Wrappers.<PlaylistDetail>lambdaQuery()
                .eq(PlaylistDetail::getPlid, plid)
                .eq(PlaylistDetail::getSid, sid));
    }

    /**
     * 按 (plid,sid) 判断歌曲是否已在歌单中。
     *
     * @param plid 歌单 plid
     * @param sid  歌曲 sid
     * @return 已收录返回 true
     */
    private boolean existsSong(Long plid, Long sid) {
        return playlistDetailMapper.selectCount(Wrappers.<PlaylistDetail>lambdaQuery()
                .eq(PlaylistDetail::getPlid, plid)
                .eq(PlaylistDetail::getSid, sid)) > 0;
    }

    /**
     * 按一批 sid 批量加载歌曲，返回 sid→Song 映射，供详情回填。
     *
     * @param sids 歌曲 sid 列表（可含重复，会自动去重）
     * @return sid → Song 映射；空列表返回空表
     */
    private Map<Long, Song> loadSongs(List<Long> sids) {
        if (sids == null || sids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> distinct = sids.stream().distinct().toList();
        List<Song> songs = songMapper.selectBatchIds(distinct);
        return songs.stream().collect(Collectors.toMap(Song::getSid, Function.identity()));
    }
}
