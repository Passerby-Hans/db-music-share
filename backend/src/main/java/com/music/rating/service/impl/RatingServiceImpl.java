package com.music.rating.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.music.common.exception.BizException;
import com.music.common.result.PageVO;
import com.music.common.result.ResultCode;
import com.music.rating.dto.MyRatingVO;
import com.music.rating.dto.RatingStatVO;
import com.music.rating.entity.Rating;
import com.music.rating.mapper.RatingMapper;
import com.music.rating.service.RatingService;
import com.music.song.entity.Song;
import com.music.song.mapper.SongMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 评分业务实现。
 *
 * <p>提交评分为 upsert（查 (uid,sid) 命中则改分、否则插入）；撤销/概况/列表
 * 均为 {@code rating} 单表操作。概况用 {@code AVG/COUNT} 聚合一次查出平均分与人数；
 * 「我的评分」为防 N+1：先分页查评分记录，再批量查歌曲回填，固定 2 次查询，
 * 与页大小无关——同收藏列表的实现思路。</p>
 *
 * <p>可见性：提交评分要求歌曲「口径A 公开可见」；撤销与概况不校验可见性
 * （已下架的歌仍可撤分、仍可看其历史评分统计）。详见 {@link RatingService}。</p>
 */
@Service
public class RatingServiceImpl implements RatingService {

    /** 歌曲审核状态：通过（口径A 可见条件之一，决定能否被评分）。 */
    private static final int SONG_AUDIT_PASSED = 1;

    private final RatingMapper ratingMapper;
    private final SongMapper songMapper;

    /**
     * 构造器注入依赖。
     *
     * @param ratingMapper 评分数据访问
     * @param songMapper   歌曲数据访问（评分前校验可见、列表回填歌曲信息）
     */
    public RatingServiceImpl(RatingMapper ratingMapper, SongMapper songMapper) {
        this.ratingMapper = ratingMapper;
        this.songMapper = songMapper;
    }

    /**
     * 提交评分（upsert）。先校验歌曲口径A可见，再查是否已评：
     * 已评则更新分数与时间（改分），未评则插入新记录。
     */
    @Override
    public void rate(Long uid, Long sid, Integer score) {
        // 只能给"公开可见"的歌评分（已审核且未删），否则视为不存在
        Song song = songMapper.selectById(sid);
        if (song == null
                || Boolean.TRUE.equals(song.getIsDeleted())
                || song.getAuditStatus() == null
                || song.getAuditStatus() != SONG_AUDIT_PASSED) {
            throw new BizException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        // upsert：查当前用户对该歌的评分是否已存在
        Rating existing = ratingMapper.selectOne(Wrappers.<Rating>lambdaQuery()
                .eq(Rating::getUid, uid)
                .eq(Rating::getSid, sid));
        if (existing != null) {
            // 已评过 → 改分：更新分数，并刷新评分时间（让"我的评分"列表回到最前）
            existing.setScore(score);
            existing.setRateTime(java.time.OffsetDateTime.now());
            ratingMapper.updateById(existing);
        } else {
            // 未评过 → 插入新评分；rate_time 由 DB DEFAULT CURRENT_TIMESTAMP 填充
            Rating r = new Rating();
            r.setUid(uid);
            r.setSid(sid);
            r.setScore(score);
            ratingMapper.insert(r);
        }
    }

    /**
     * 撤销评分（幂等）。按 (uid,sid) 删除，无记录时影响 0 行亦视为成功。
     * 不校验歌曲可见性：已下架的歌也允许撤分。
     */
    @Override
    public void cancel(Long uid, Long sid) {
        ratingMapper.delete(Wrappers.<Rating>lambdaQuery()
                .eq(Rating::getUid, uid)
                .eq(Rating::getSid, sid));
    }

    /**
     * 某歌评分概况。用一次聚合查询取平均分与人数，再按需查当前用户自己的评分。
     * 不校验歌曲可见性——下架歌的历史评分统计仍可查看。
     */
    @Override
    public RatingStatVO getStat(Long sid, Long currentUid) {
        // 聚合查询：AVG(score)、COUNT(*)。别名全小写，避开 PG 把未加引号别名
        // 折叠为小写、用驼峰 key 从 selectMaps 取值落空的坑（评论模块曾踩过）。
        QueryWrapper<Rating> wrapper = new QueryWrapper<>();
        wrapper.select("AVG(score) AS avg_score", "COUNT(*) AS cnt")
                .eq("sid", sid);
        Map<String, Object> row = ratingMapper.selectMaps(wrapper).stream().findFirst().orElse(null);

        long count = 0L;
        double avg = 0.0;
        if (row != null && row.get("cnt") != null) {
            count = ((Number) row.get("cnt")).longValue();
            // 无人评分时 AVG 为 null；有评分则保留一位小数便于展示
            Object avgObj = row.get("avg_score");
            if (avgObj != null) {
                avg = Math.round(((Number) avgObj).doubleValue() * 10.0) / 10.0;
            }
        }

        // 回填"我的评分"：游客（currentUid==null）一律 null；登录用户查其本人评分
        Integer myScore = null;
        if (currentUid != null) {
            Rating mine = ratingMapper.selectOne(Wrappers.<Rating>lambdaQuery()
                    .eq(Rating::getUid, currentUid)
                    .eq(Rating::getSid, sid));
            if (mine != null) {
                myScore = mine.getScore();
            }
        }
        return new RatingStatVO(sid, avg, count, myScore);
    }

    /**
     * 我的评分分页（按 rate_time 倒序）。分页查评分记录后，批量查歌曲回填，
     * 每项计算 playable；失效歌曲不剔除，仅标记不可播放。
     */
    @Override
    public PageVO<MyRatingVO> listMine(Long uid, long page, long size) {
        LambdaQueryWrapper<Rating> wrapper = Wrappers.<Rating>lambdaQuery()
                .eq(Rating::getUid, uid)
                .orderByDesc(Rating::getRateTime);
        IPage<Rating> result = ratingMapper.selectPage(new Page<>(page, size), wrapper);
        List<Rating> records = result.getRecords();

        // 批量查本页评分对应的歌曲（含已软删/驳回的），避免逐条查 song
        Map<Long, Song> songMap = loadSongs(records.stream().map(Rating::getSid).toList());

        List<MyRatingVO> vos = records.stream()
                // 理论上歌曲必存在（外键 + CASCADE）；防御性跳过已被物理清理的脏数据
                .filter(r -> songMap.containsKey(r.getSid()))
                .map(r -> MyRatingVO.from(songMap.get(r.getSid()), r.getScore(), r.getRateTime()))
                .toList();
        return new PageVO<>(vos, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 按一批 sid 批量加载歌曲，返回 sid→Song 映射，供列表回填。
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
