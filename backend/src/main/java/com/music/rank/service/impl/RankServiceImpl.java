package com.music.rank.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.music.common.storage.StorageService;
import com.music.rank.BoardType;
import com.music.rank.dto.RankItemVO;
import com.music.rank.service.RankService;
import com.music.playrecord.entity.PlayRecord;
import com.music.playrecord.mapper.PlayRecordMapper;
import com.music.song.entity.Song;
import com.music.song.mapper.SongMapper;
import com.music.user.entity.User;
import com.music.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 排行榜业务实现。
 *
 * <p><b>读榜</b>({@link #list}):解析 key → Redis {@code ZREVRANGE 0 9 WITHSCORES};
 * 若 Redis 空(key 不存在/连不上)则降级聚合 {@code play_record}(idx_play_time_sid 索引);
 * 再批量回填歌曲信息(标题/封面直链/上传者昵称),固定 2~3 次查询防 N+1。</p>
 *
 * <p><b>对账</b>({@link #rebuild}):从 play_record 全量聚合(不加 LIMIT)→ DEL key +
 * ZADD 覆盖写回,消除 ZINCRBY 失败/多算的累积漂移、重建 Redis 丢失。日/周榜补 TTL。</p>
 *
 * <p><b>真相源</b>:play_record(DB);Redis ZSET 是准实时缓存。设计文档 §7.4。</p>
 */
@Service
public class RankServiceImpl implements RankService {

    private static final Logger log = LoggerFactory.getLogger(RankServiceImpl.class);

    /** Top N:固定取前 10(榜单语义)。 */
    private static final int TOP_N = 10;

    /** 日榜 TTL,与点唱写入口径一致。 */
    private static final Duration DAILY_TTL = Duration.ofHours(48);
    /** 周榜 TTL。 */
    private static final Duration WEEKLY_TTL = Duration.ofDays(9);

    private static final String RANK_TOTAL = "rank:total";
    private static final String RANK_DAILY_PREFIX = "rank:daily:";
    private static final String RANK_WEEKLY_PREFIX = "rank:weekly:";

    /** 日榜 key 日期格式(与点唱 PlayRecordServiceImpl.DAY_FMT 一致)。 */
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** ISO 周号格式(与点唱 PlayRecordServiceImpl.WEEK_FMT 一致)。 */
    private static final DateTimeFormatter WEEK_FMT = new DateTimeFormatterBuilder()
            .appendValue(WeekFields.ISO.weekBasedYear(), 4)
            .appendLiteral("-W")
            .appendValue(WeekFields.ISO.weekOfWeekBasedYear(), 2)
            .toFormatter(Locale.ROOT);

    private final StringRedisTemplate redis;
    private final PlayRecordMapper playRecordMapper;
    private final SongMapper songMapper;
    private final UserMapper userMapper;
    private final StorageService storageService;

    public RankServiceImpl(StringRedisTemplate redis,
                           PlayRecordMapper playRecordMapper,
                           SongMapper songMapper,
                           UserMapper userMapper,
                           StorageService storageService) {
        this.redis = redis;
        this.playRecordMapper = playRecordMapper;
        this.songMapper = songMapper;
        this.userMapper = userMapper;
        this.storageService = storageService;
    }

    @Override
    public List<RankItemVO> list(BoardType board) {
        String key = resolveKey(board);
        List<Map.Entry<Long, Long>> sidScores = readFromRedis(key);
        if (sidScores.isEmpty()) {
            // Redis 空(key 不存在/连不上)→ 降级聚合 play_record,保证读榜总有数据
            sidScores = aggregate(board, TOP_N);
        }
        return buildItems(sidScores);
    }

    /**
     * 对账重建某榜单:从 play_record 全量聚合(无 LIMIT)覆盖写回 Redis ZSET。
     *
     * <p>覆盖方式为 DEL 旧 key + ZADD 全量重建——<b>非原子</b>:对账窗口内点唱的
     * ZINCRBY 可能落在 DEL 后/ZADD 前的空 key 上、随后被 ZADD 覆盖丢失。这是后台
     * 任务的瞬时空窗,设计文档 §11 声明可容忍(读榜有降级聚合兜底、次日对账再纠正)。</p>
     *
     * @param board 榜单类型(TOTAL 全量/DAILY 当天/WEEKLY 本周)
     */
    @Override
    public void rebuild(BoardType board) {
        // 全量聚合(无 LIMIT):重建完整 ZSET;limit<=0 表示不 ORDER BY/LIMIT
        List<Map<String, Object>> rows = playRecordMapper.selectMaps(buildAggregateQuery(board, 0));

        String key = resolveKey(board);
        // 覆盖写回:DEL 旧值 → ZADD 全量重建。非原子(见方法注释):对账窗口内点唱 ZINCRBY 可能被覆盖,可容忍。
        redis.delete(key);
        if (!rows.isEmpty()) {
            Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
            for (Map<String, Object> row : rows) {
                Object sidObj = row.get("sid");
                Object cntObj = row.get("cnt");
                if (sidObj == null || cntObj == null) {
                    continue;
                }
                tuples.add(new DefaultTypedTuple<>(
                        String.valueOf(((Number) sidObj).longValue()),
                        (double) ((Number) cntObj).longValue()));
            }
            if (!tuples.isEmpty()) {
                redis.opsForZSet().add(key, tuples);
            }
        }
        // 日/周榜补 TTL(与点唱写入口径一致;总榜无 TTL)
        if (board == BoardType.DAILY) {
            redis.expire(key, DAILY_TTL);
        } else if (board == BoardType.WEEKLY) {
            redis.expire(key, WEEKLY_TTL);
        }
    }

    /**
     * 解析某榜的 Redis key。日/周含当天/本周日期,与点唱写入格式严格一致。
     */
    private String resolveKey(BoardType board) {
        OffsetDateTime now = OffsetDateTime.now();
        return switch (board) {
            case TOTAL -> RANK_TOTAL;
            case DAILY -> RANK_DAILY_PREFIX + now.format(DAY_FMT);
            case WEEKLY -> RANK_WEEKLY_PREFIX + now.format(WEEK_FMT);
        };
    }

    /**
     * 从 Redis 读 TopN(已按 score 倒序)。任何异常或空都返回空表,交由调用方降级。
     */
    private List<Map.Entry<Long, Long>> readFromRedis(String key) {
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    redis.opsForZSet().reverseRangeWithScores(key, 0, TOP_N - 1);
            if (tuples == null || tuples.isEmpty()) {
                return List.of();
            }
            List<Map.Entry<Long, Long>> result = new ArrayList<>(tuples.size());
            for (ZSetOperations.TypedTuple<String> t : tuples) {
                if (t.getValue() == null) {
                    continue;
                }
                // ZSET score 是 Double(ZINCRBY 以 1.0 递增);计数场景恒为整,转 long
                result.add(Map.entry(Long.parseLong(t.getValue()), t.getScore().longValue()));
            }
            return result;
        } catch (Exception e) {
            log.warn("Redis 读榜失败,降级聚合: key={}, err={}", key, e.getMessage());
            return List.of();
        }
    }

    /**
     * 降级聚合:从 play_record 按时间窗 GROUP BY sid 取 TopN。
     * 别名全小写(sid/cnt),避开 PG 把未加引号驼峰别名折叠小写、selectMaps 取不到的坑。
     *
     * @param board 榜单(决定时间窗:TOTAL 全量、DAILY 当天、WEEKLY 本周)
     * @param limit 取前 N(读榜=10;对账传大数或单独方法,见 Task 4)
     */
    private List<Map.Entry<Long, Long>> aggregate(BoardType board, int limit) {
        // TopN 聚合(limit>0 带 ORDER BY cnt DESC + LIMIT),公共查询构建见 buildAggregateQuery
        List<Map<String, Object>> rows = playRecordMapper.selectMaps(buildAggregateQuery(board, limit));
        List<Map.Entry<Long, Long>> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object sidObj = row.get("sid");
            Object cntObj = row.get("cnt");
            if (sidObj == null || cntObj == null) {
                continue;
            }
            result.add(Map.entry(((Number) sidObj).longValue(), ((Number) cntObj).longValue()));
        }
        return result;
    }

    /**
     * 构建 play_record 时间窗聚合查询(sid → cnt)。limit<=0 表示全量不裁剪(对账重建用),
     * limit>0 表示取 TopN(读榜降级用)。别名 sid/cnt 全小写,避开 PG 折叠坑。
     *
     * @param board 榜单(决定时间窗)
     * @param limit 取前 N;<=0 则不 ORDER BY/LIMIT(全量)
     * @return 已配好的 QueryWrapper(调用方自行 selectMaps)
     */
    private QueryWrapper<PlayRecord> buildAggregateQuery(BoardType board, int limit) {
        OffsetDateTime start = windowStart(board);
        QueryWrapper<PlayRecord> w = new QueryWrapper<>();
        w.select("sid", "COUNT(*) AS cnt");
        if (start != null) {
            w.ge("play_time", start);
        }
        w.groupBy("sid");
        if (limit > 0) {
            w.orderByDesc("cnt").last("LIMIT " + limit);
        }
        return w;
    }

    /**
     * 某榜时间窗起点:TOTAL 全量(null)、DAILY 今日 0 点、WEEKLY 本周一 0 点(ISO)。
     */
    private OffsetDateTime windowStart(BoardType board) {
        OffsetDateTime now = OffsetDateTime.now();
        return switch (board) {
            case TOTAL -> null;
            case DAILY -> now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
            case WEEKLY -> {
                LocalDate monday = now.toLocalDate().with(WeekFields.ISO.dayOfWeek(), 1);
                yield monday.atStartOfDay().atOffset(now.getOffset());
            }
        };
    }

    /**
     * 批量回填歌曲信息(标题/封面直链/上传者昵称)并赋排名。固定 2~3 次查询,防 N+1。
     * 参考 rating loadSongs 模式。
     */
    private List<RankItemVO> buildItems(List<Map.Entry<Long, Long>> sidScores) {
        if (sidScores.isEmpty()) {
            return List.of();
        }
        List<Long> sids = sidScores.stream().map(Map.Entry::getKey).toList();
        // 1. 批量查歌曲
        Map<Long, Song> songMap = songMapper.selectBatchIds(sids).stream()
                .collect(Collectors.toMap(Song::getSid, Function.identity()));
        // 2. 批量查上传者昵称(本页涉及到的 uploader uid 去重)
        Set<Long> uploaderUids = songMap.values().stream()
                .map(Song::getUploaderUid)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> nameMap = uploaderUids.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(uploaderUids).stream()
                        .collect(Collectors.toMap(User::getUid, User::getNickname));
        // 3. 拼装(保留 sidScores 的倒序,赋 rank)
        List<RankItemVO> items = new ArrayList<>(sidScores.size());
        int rank = 1;
        for (Map.Entry<Long, Long> e : sidScores) {
            Song song = songMap.get(e.getKey());
            if (song == null) {
                continue; // 防御:歌被物理删(理论不会,外键 CASCADE)
            }
            RankItemVO vo = new RankItemVO();
            vo.setRank(rank++);
            vo.setSid(song.getSid());
            vo.setTitle(song.getTitle());
            vo.setCover(storageService.publicUrl(song.getCover()));
            vo.setUploaderName(nameMap.get(song.getUploaderUid()));
            vo.setScore(e.getValue());
            items.add(vo);
        }
        return items;
    }
}
