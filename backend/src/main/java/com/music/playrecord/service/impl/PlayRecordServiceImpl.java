package com.music.playrecord.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.music.common.exception.BizException;
import com.music.common.result.ResultCode;
import com.music.playrecord.entity.PlayRecord;
import com.music.playrecord.mapper.PlayRecordMapper;
import com.music.playrecord.service.PlayRecordService;
import com.music.song.entity.Song;
import com.music.song.mapper.SongMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.WeekFields;
import java.util.Locale;

/**
 * 点唱记录业务实现。
 *
 * <p>核心流程(见设计文档 §4 数据流):</p>
 * <ol>
 *   <li>校验歌曲口径A可见(已审未删),否则 404——与 getPlayUrl 口径一致;</li>
 *   <li>同 uid+sid 60s 去重(Redis SETNX),窗口内重复幂等返回,不计数;</li>
 *   <li>事务内:INSERT play_record 明细 + UPDATE song.play_count 原子自增
 *       (setSql 单条 SQL 行锁串行化,并发不丢计数,禁止 read-modify-write);</li>
 *   <li>Redis 三榜(日/周/总)ZINCRBY +1,准实时榜单缓存。</li>
 * </ol>
 *
 * <p><b>一致性</b>:DB 是真相源,play_count 同步自增恒等于真实行数;Redis 仅榜单缓存,
 * 写失败只记 warn 不阻断点唱(设计文档 §9 降级)。</p>
 *
 * <p><b>Redis 客户端</b>:用 {@link StringRedisTemplate} 而非项目自定义的
 * {@code RedisTemplate<String,Object>}(后者 JSON 序列化会把 ZSET member / 计数 value
 * 包一层 JSON,污染榜单 key)。StringRedisTemplate 由 Spring Boot 自动配置,String
 * 序列化最干净,是 ZSET/计数器场景惯例。</p>
 */
@Service
public class PlayRecordServiceImpl implements PlayRecordService {

    private static final Logger log = LoggerFactory.getLogger(PlayRecordServiceImpl.class);

    /** 歌曲审核状态:通过(口径A 可见条件之一)。 */
    private static final int SONG_AUDIT_PASSED = 1;

    /** 同 uid+sid 去重窗口。 */
    private static final Duration DEDUP_WINDOW = Duration.ofSeconds(60);

    /** 日榜 key TTL(跨天后自然失效,留足观察窗口)。 */
    private static final Duration DAILY_TTL = Duration.ofHours(48);
    /** 周榜 key TTL。 */
    private static final Duration WEEKLY_TTL = Duration.ofDays(9);

    private static final String RANK_TOTAL = "rank:total";
    private static final String RANK_DAILY_PREFIX = "rank:daily:";
    private static final String RANK_WEEKLY_PREFIX = "rank:weekly:";
    private static final String DEDUP_PREFIX = "play:dedup:";

    /** 日榜 key 日期格式 yyyy-MM-dd。 */
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** ISO 周号格式 yyyy-Www(两位周号补零)。 */
    // 注:JDK 中格式器构造器类名为 DateTimeFormatterBuilder(无名为 Builder 的嵌套类),
    // 故直接 import 该顶层类构造,而非计划初稿笔误的 DateTimeFormatter.Builder。
    private static final DateTimeFormatter WEEK_FMT = new DateTimeFormatterBuilder()
            .appendValue(WeekFields.ISO.weekBasedYear(), 4)
            .appendLiteral("-W")
            .appendValue(WeekFields.ISO.weekOfWeekBasedYear(), 2)
            .toFormatter(Locale.ROOT);

    private final PlayRecordMapper playRecordMapper;
    private final SongMapper songMapper;
    private final StringRedisTemplate redis;

    /**
     * 构造器注入。
     *
     * @param playRecordMapper 点唱记录数据访问
     * @param songMapper       歌曲数据访问(口径校验 + play_count 自增)
     * @param redis            String Redis(ZSET 三榜 + 去重 SETNX)
     */
    public PlayRecordServiceImpl(PlayRecordMapper playRecordMapper,
                                 SongMapper songMapper,
                                 StringRedisTemplate redis) {
        this.playRecordMapper = playRecordMapper;
        this.songMapper = songMapper;
        this.redis = redis;
    }

    @Override
    @Transactional
    public void record(Long uid, Long sid) {
        // 1. 校验口径A可见(与 getPlayUrl / 评分 同口径):不可见的歌不该被记点唱
        Song song = songMapper.selectById(sid);
        if (song == null
                || Boolean.TRUE.equals(song.getIsDeleted())
                || song.getAuditStatus() == null
                || song.getAuditStatus() != SONG_AUDIT_PASSED) {
            throw new BizException(ResultCode.NOT_FOUND, "歌曲不存在");
        }

        // 2. 同 uid+sid 60s 去重:SETNX 既判断又占位(一次操作)
        String dedupKey = DEDUP_PREFIX + uid + ":" + sid;
        Boolean fresh = redis.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_WINDOW);
        if (Boolean.FALSE.equals(fresh)) {
            // 窗口内重复点唱,幂等返回,不计数、不记明细、不 ZINCRBY
            return;
        }
        // 注:SETNX 成功但后续事务失败的极端情况,去重 key 已占,60s 内合法重试被吞一次,
        // 影响极小(用户多半还会再放),事务失败本属异常,见设计文档 §8 取舍。

        // 3. 事务内:写明细 + play_count 原子自增
        PlayRecord rec = new PlayRecord();
        rec.setUid(uid);
        rec.setSid(sid);
        rec.setPlayTime(OffsetDateTime.now());
        playRecordMapper.insert(rec);

        // play_count 原子自增:单条 SQL 行锁串行化,并发请求串行,绝不丢计数。
        // 禁止 selectById 读出再 setPlayCount+1 的 read-modify-write(并发丢更新)。
        songMapper.update(null, Wrappers.<Song>lambdaUpdate()
                .setSql("play_count = play_count + 1")
                .eq(Song::getSid, sid));

        // 4. Redis 三榜 ZINCRBY(事务方法体内、DB 操作之后)。
        // Redis 不参与 DB 事务回滚——极端情况 Redis 多算,由排行榜阶段对账兜底。
        bumpRank(sid);
    }

    /**
     * 向 Redis 三榜(总/日/周)各 +1,并刷新日/周榜 TTL。
     * 任何 Redis 异常被吞掉只记 warn——点唱核心契约在 DB,Redis 仅缓存,挂了不影响点唱。
     *
     * @param sid 歌曲 sid
     */
    private void bumpRank(Long sid) {
        String member = String.valueOf(sid);
        OffsetDateTime now = OffsetDateTime.now();
        String dailyKey = RANK_DAILY_PREFIX + now.format(DAY_FMT);
        String weeklyKey = RANK_WEEKLY_PREFIX + now.format(WEEK_FMT);
        try {
            redis.opsForZSet().incrementScore(RANK_TOTAL, member, 1.0);
            redis.opsForZSet().incrementScore(dailyKey, member, 1.0);
            redis.expire(dailyKey, DAILY_TTL);
            redis.opsForZSet().incrementScore(weeklyKey, member, 1.0);
            redis.expire(weeklyKey, WEEKLY_TTL);
        } catch (Exception e) {
            log.warn("Redis 榜单写入失败,点唱已记录,榜单暂缺(对账兜底): sid={}, err={}",
                    sid, e.getMessage());
        }
    }
}
