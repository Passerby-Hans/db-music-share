package com.music.favorite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.music.common.exception.BizException;
import com.music.common.result.PageVO;
import com.music.common.result.ResultCode;
import com.music.favorite.dto.FavoriteVO;
import com.music.favorite.entity.Favorite;
import com.music.favorite.mapper.FavoriteMapper;
import com.music.favorite.service.FavoriteService;
import com.music.song.entity.Song;
import com.music.song.mapper.SongMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 收藏业务实现。
 *
 * <p>收藏/取消/状态查询均为 {@code favorite} 单表操作，依赖 {@code (uid,sid)}
 * 唯一约束实现幂等。「我的收藏列表」为防 N+1：先分页查本页收藏记录，
 * 再一次性批量查出涉及的歌曲信息回填，固定 2 次 DB 访问，与页大小无关。</p>
 *
 * <p>可见性策略：收藏动作要求歌曲「口径A 公开可见」；但列表展示与取消收藏
 * 都<strong>不</strong>过滤失效歌曲——已下架的歌仍列出（playable=false）、
 * 仍可取消收藏，保证用户对自己收藏记录的完整掌控。</p>
 */
@Service
public class FavoriteServiceImpl implements FavoriteService {

    /** 歌曲审核状态：通过（口径A 可见条件之一，决定能否被收藏）。 */
    private static final int SONG_AUDIT_PASSED = 1;

    private final FavoriteMapper favoriteMapper;
    private final SongMapper songMapper;

    /**
     * 构造器注入依赖。
     *
     * @param favoriteMapper 收藏数据访问
     * @param songMapper     歌曲数据访问（收藏前校验可见、列表回填歌曲信息）
     */
    public FavoriteServiceImpl(FavoriteMapper favoriteMapper, SongMapper songMapper) {
        this.favoriteMapper = favoriteMapper;
        this.songMapper = songMapper;
    }

    /**
     * 收藏一首歌（幂等）。先校验歌曲口径A可见，再查是否已收藏，
     * 未收藏才插入；已收藏直接返回（不抛错、不重复插入）。
     */
    @Override
    public void add(Long uid, Long sid) {
        // 只能收藏"公开可见"的歌（已审核且未删），否则视为不存在
        Song song = songMapper.selectById(sid);
        if (song == null
                || Boolean.TRUE.equals(song.getIsDeleted())
                || song.getAuditStatus() == null
                || song.getAuditStatus() != SONG_AUDIT_PASSED) {
            throw new BizException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        // 幂等：已存在收藏关系则直接返回，避免触发 (uid,sid) 唯一约束异常
        if (existsFavorite(uid, sid)) {
            return;
        }
        Favorite favorite = new Favorite();
        favorite.setUid(uid);
        favorite.setSid(sid);
        favoriteMapper.insert(favorite);
    }

    /**
     * 取消收藏（幂等）。按 (uid,sid) 删除，无记录时影响 0 行亦视为成功。
     * 不校验歌曲可见性：已下架的歌也允许取消收藏。
     */
    @Override
    public void remove(Long uid, Long sid) {
        LambdaQueryWrapper<Favorite> wrapper = Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUid, uid)
                .eq(Favorite::getSid, sid);
        favoriteMapper.delete(wrapper);
    }

    /**
     * 我的收藏分页（按 fav_time 倒序）。分页查收藏记录后，批量查歌曲回填，
     * 每项计算 playable 标志；失效歌曲不剔除，仅标记不可播放。
     */
    @Override
    public PageVO<FavoriteVO> listMine(Long uid, long page, long size) {
        LambdaQueryWrapper<Favorite> wrapper = Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUid, uid)
                .orderByDesc(Favorite::getFavTime);
        IPage<Favorite> result = favoriteMapper.selectPage(new Page<>(page, size), wrapper);
        List<Favorite> records = result.getRecords();

        // 批量查本页收藏对应的歌曲（含已软删/驳回的），避免逐条查 song
        Map<Long, Song> songMap = loadSongs(records.stream().map(Favorite::getSid).toList());

        List<FavoriteVO> vos = records.stream()
                // 理论上歌曲必存在（外键约束 + CASCADE）；防御性跳过已被物理清理的脏数据
                .filter(f -> songMap.containsKey(f.getSid()))
                .map(f -> FavoriteVO.from(songMap.get(f.getSid()), f.getFavTime()))
                .toList();
        return new PageVO<>(vos, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 查询是否已收藏。直接按 (uid,sid) 计数判断存在性。
     */
    @Override
    public boolean isFavorited(Long uid, Long sid) {
        return existsFavorite(uid, sid);
    }

    /**
     * 按 (uid,sid) 判断收藏关系是否存在。
     *
     * @param uid 用户 uid
     * @param sid 歌曲 sid
     * @return 存在返回 true
     */
    private boolean existsFavorite(Long uid, Long sid) {
        LambdaQueryWrapper<Favorite> wrapper = Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUid, uid)
                .eq(Favorite::getSid, sid);
        return favoriteMapper.selectCount(wrapper) > 0;
    }

    /**
     * 按一批 sid 批量加载歌曲，返回 sid→Song 映射，供列表回填。
     *
     * @param sids 歌曲 sid 列表（可含重复，会自动去重）
     * @return sid → Song 的映射；空列表返回空表
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
