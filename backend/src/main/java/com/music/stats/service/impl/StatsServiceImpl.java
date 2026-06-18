package com.music.stats.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.common.storage.StorageService;
import com.music.playrecord.entity.PlayRecord;
import com.music.playrecord.mapper.PlayRecordMapper;
import com.music.song.entity.Song;
import com.music.song.mapper.SongMapper;
import com.music.stats.dto.TopUploaderVO;
import com.music.stats.dto.TopUserVO;
import com.music.stats.service.StatsService;
import com.music.user.entity.User;
import com.music.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 统计报表业务实现。
 *
 * <p><b>读统计</b>({@link #topUsers}/{@link #topUploaders}):cache-aside——读 Redis 缓存,
 * 命中则反序列化返回;miss 则实时聚合 play_record/song + 批量回填用户信息 + 写缓存(TTL 1h)。</p>
 *
 * <p><b>聚合</b>:用户活跃度按 uid 聚合 play_record COUNT(走 idx_play_uid);
 * 上传者贡献按 uploader_uid 聚合 song(COUNT 歌 + SUM play_count,过滤软删)。
 * 别名全小写避 PG 折叠坑。</p>
 *
 * <p><b>缓存客户端</b>:StringRedisTemplate + ObjectMapper 手动 JSON 序列化
 * (与点唱/排行榜一致,避免项目 RedisTemplate<String,Object> 的 JSON 类型包装干扰)。</p>
 */
@Service
public class StatsServiceImpl implements StatsService {

    private static final Logger log = LoggerFactory.getLogger(StatsServiceImpl.class);

    private static final int TOP_N = 10;
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private static final String KEY_TOP_USERS = "stats:top-users";
    private static final String KEY_TOP_UPLOADERS = "stats:top-uploaders";

    private final PlayRecordMapper playRecordMapper;
    private final SongMapper songMapper;
    private final UserMapper userMapper;
    private final StorageService storageService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public StatsServiceImpl(PlayRecordMapper playRecordMapper,
                            SongMapper songMapper,
                            UserMapper userMapper,
                            StorageService storageService,
                            StringRedisTemplate redis,
                            ObjectMapper objectMapper) {
        this.playRecordMapper = playRecordMapper;
        this.songMapper = songMapper;
        this.userMapper = userMapper;
        this.storageService = storageService;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<TopUserVO> topUsers() {
        // cache-aside:读缓存,命中则反序列化返回(空列表也视为命中,不重复聚合)
        List<TopUserVO> cached = readCache(KEY_TOP_USERS, new TypeReference<List<TopUserVO>>() {
        });
        if (cached != null) {
            return cached;
        }
        List<TopUserVO> fresh = aggregateTopUsers();
        writeCache(KEY_TOP_USERS, fresh);
        return fresh;
    }

    @Override
    public List<TopUploaderVO> topUploaders() {
        List<TopUploaderVO> cached = readCache(KEY_TOP_UPLOADERS, new TypeReference<List<TopUploaderVO>>() {
        });
        if (cached != null) {
            return cached;
        }
        List<TopUploaderVO> fresh = aggregateTopUploaders();
        writeCache(KEY_TOP_UPLOADERS, fresh);
        return fresh;
    }

    @Override
    public void refreshAll() {
        // Task 4 实现
        throw new UnsupportedOperationException("refreshAll 在 Task 4 实现");
    }

    /**
     * 聚合用户活跃度 TOP10(按点唱次数)。聚合 + 回填昵称/头像,不写缓存(由调用方写)。
     */
    private List<TopUserVO> aggregateTopUsers() {
        QueryWrapper<PlayRecord> w = new QueryWrapper<>();
        w.select("uid", "COUNT(*) AS cnt")
                .groupBy("uid").orderByDesc("cnt").last("LIMIT " + TOP_N);
        List<Map<String, Object>> rows = playRecordMapper.selectMaps(w);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> uids = rows.stream().map(r -> ((Number) r.get("uid")).longValue()).toList();
        Map<Long, User> userMap = loadUsers(uids);
        List<TopUserVO> result = new ArrayList<>(rows.size());
        int rank = 1;
        for (Map<String, Object> row : rows) {
            Long uid = ((Number) row.get("uid")).longValue();
            User u = userMap.get(uid);
            if (u == null) {
                continue; // 防御:用户被物理删(理论不会)
            }
            TopUserVO vo = new TopUserVO();
            vo.setRank(rank++);
            vo.setUid(uid);
            vo.setNickname(u.getNickname());
            vo.setAvatar(storageService.publicUrl(u.getAvatar()));
            vo.setPlayCount(((Number) row.get("cnt")).longValue());
            result.add(vo);
        }
        return result;
    }

    /**
     * 聚合上传者贡献 TOP10(按总播放量)。COUNT 歌 + SUM(play_count),过滤软删歌。
     */
    private List<TopUploaderVO> aggregateTopUploaders() {
        QueryWrapper<Song> w = new QueryWrapper<>();
        w.select("uploader_uid", "COUNT(*) AS song_cnt", "SUM(play_count) AS total_play")
                .eq("is_deleted", false)
                .groupBy("uploader_uid")
                .orderByDesc("total_play")
                .last("LIMIT " + TOP_N);
        List<Map<String, Object>> rows = songMapper.selectMaps(w);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> uids = rows.stream().map(r -> ((Number) r.get("uploader_uid")).longValue()).toList();
        Map<Long, User> userMap = loadUsers(uids);
        List<TopUploaderVO> result = new ArrayList<>(rows.size());
        int rank = 1;
        for (Map<String, Object> row : rows) {
            Long uid = ((Number) row.get("uploader_uid")).longValue();
            User u = userMap.get(uid);
            if (u == null) {
                continue;
            }
            TopUploaderVO vo = new TopUploaderVO();
            vo.setRank(rank++);
            vo.setUid(uid);
            vo.setNickname(u.getNickname());
            vo.setAvatar(storageService.publicUrl(u.getAvatar()));
            vo.setSongCount(((Number) row.get("song_cnt")).longValue());
            // SUM 无歌时可能 null,但 WHERE is_deleted=false 且 GROUP BY 有行必有歌;防御取值
            Object totalObj = row.get("total_play");
            vo.setTotalPlayCount(totalObj == null ? 0L : ((Number) totalObj).longValue());
            result.add(vo);
        }
        return result;
    }

    /** 按 uid 批量加载用户(uid→User),供回填昵称/头像。 */
    private Map<Long, User> loadUsers(List<Long> uids) {
        if (uids == null || uids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(uids).stream()
                .collect(Collectors.toMap(User::getUid, Function.identity()));
    }

    /**
     * 读缓存并反序列化。任何异常或 key 不存在都返回 null(交由调用方聚合)。
     * 注:缓存存 "[]" 时反序列化为空列表(非 null),表示"已聚合且无数据",不重复聚合。
     */
    private <T> List<T> readCache(String key, TypeReference<List<T>> typeRef) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.warn("读统计缓存失败,降级聚合: key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /** 序列化列表写缓存(TTL)。写失败只记 warn,不影响读(下次读 miss 再聚合)。 */
    private void writeCache(String key, List<?> list) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(list), CACHE_TTL);
        } catch (Exception e) {
            log.warn("写统计缓存失败: key={}, err={}", key, e.getMessage());
        }
    }
}
