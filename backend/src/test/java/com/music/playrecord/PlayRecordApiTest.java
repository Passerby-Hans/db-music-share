package com.music.playrecord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.music.playrecord.entity.PlayRecord;
import com.music.playrecord.mapper.PlayRecordMapper;
import com.music.song.entity.Song;
import com.music.song.mapper.SongMapper;
import com.music.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 点唱记录模块 API 集成测试({@code /api/play-record})。
 *
 * <p>覆盖正常点唱(play_count+1、明细落行、Redis 三榜写入)、60s 去重吞重、
 * 不同用户各 +1、不可见歌 404、未登录 401、play_count==真实行数。</p>
 *
 * <p>种子:alice=uid5/bob=uid6/carol=uid7,密码 123456;sid=1~8 多数已通过,
 * sid=9/10 待审、sid=11 驳回、sid=12 软删。</p>
 */
class PlayRecordApiTest extends AbstractIntegrationTest {

    private static final String TOKEN_HEADER = "X-Token";

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private SongMapper songMapper;
    @Autowired
    private PlayRecordMapper playRecordMapper;

    private String login(String username, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    /** 发起一次点唱,断言成功。 */
    private void record(String token, long sid) throws Exception {
        mockMvc.perform(post("/api/play-record/{sid}", sid).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200));
    }

    /** 某 sid 的总点唱数(play_count)。 */
    private long playCount(long sid) {
        return songMapper.selectById(sid).getPlayCount();
    }

    /** 某 sid 的点唱明细行数(真相源)。 */
    private long rowCount(long sid) {
        return playRecordMapper.selectCount(
                Wrappers.<PlayRecord>lambdaQuery()
                        .eq(PlayRecord::getSid, sid));
    }

    /** 某 ZSET key 下 sid 的 score(null 视为 0)。 */
    private double zscore(String key, long sid) {
        Double s = redis.opsForZSet().score(key, String.valueOf(sid));
        return s == null ? 0.0 : s;
    }

    /** 每个测试方法后清理本类写入的 Redis key(dedup + 三榜),避免污染其它用例。 */
    @AfterEach
    void cleanupRedis() {
        clearByPrefix("play:dedup:");
        clearByPrefix("rank:");
    }

    private void clearByPrefix(String prefix) {
        Set<String> keys = redis.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    @DisplayName("正常点唱:play_count+1、明细落一行、rank:total +1")
    void recordOnceIncrementsCount() throws Exception {
        String token = login("alice", "123456"); // uid5
        long sid = 1;

        long countBefore = playCount(sid);
        long rowsBefore = rowCount(sid);
        double rankBefore = zscore("rank:total", sid);

        record(token, sid);

        // play_count 原子 +1
        assertThat(playCount(sid)).isEqualTo(countBefore + 1);
        // 明细落一行
        assertThat(rowCount(sid)).isEqualTo(rowsBefore + 1);
        // 总榜 +1
        assertThat(zscore("rank:total", sid)).isEqualTo(rankBefore + 1.0);
    }

    @Test
    @DisplayName("60s 内同歌重复点唱:幂等吞掉,play_count 与明细不再增加")
    void dedupSwallowsRepeat() throws Exception {
        String token = login("alice", "123456"); // uid5
        long sid = 2;

        long countBefore = playCount(sid);
        long rowsBefore = rowCount(sid);

        record(token, sid);                       // 第一次:正常计数
        record(token, sid);                       // 第二次:60s 内,被去重吞掉

        assertThat(playCount(sid)).isEqualTo(countBefore + 1);   // 只 +1
        assertThat(rowCount(sid)).isEqualTo(rowsBefore + 1);     // 明细只 +1 行
    }

    @Test
    @DisplayName("不同用户点同一首:各自 +1,互不干扰")
    void differentUsersEachIncrement() throws Exception {
        String alice = login("alice", "123456"); // uid5
        String bob = login("bob", "123456");     // uid6
        long sid = 3;

        long countBefore = playCount(sid);
        long rowsBefore = rowCount(sid);

        record(alice, sid);
        record(bob, sid);

        assertThat(playCount(sid)).isEqualTo(countBefore + 2);
        assertThat(rowCount(sid)).isEqualTo(rowsBefore + 2);
    }

    @Test
    @DisplayName("点唱待审歌:404 且不计数")
    void recordPendingSong404() throws Exception {
        String token = login("alice", "123456");
        long sid = 9; // 待审
        long countBefore = playCount(sid);

        mockMvc.perform(post("/api/play-record/{sid}", sid).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));

        assertThat(playCount(sid)).isEqualTo(countBefore); // 未计数
    }

    @Test
    @DisplayName("点唱软删歌:404 且不计数")
    void recordDeletedSong404() throws Exception {
        String token = login("alice", "123456");
        long sid = 12; // 软删
        long countBefore = playCount(sid);

        mockMvc.perform(post("/api/play-record/{sid}", sid).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));

        assertThat(playCount(sid)).isEqualTo(countBefore);
    }

    @Test
    @DisplayName("未登录点唱:401")
    void recordAnonymous401() throws Exception {
        mockMvc.perform(post("/api/play-record/{sid}", 1))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("点唱后三榜(总/日/周)均 +1")
    void rankThreeBoardsUpdated() throws Exception {
        String token = login("alice", "123456");
        long sid = 4;

        double totalBefore = zscore("rank:total", sid);
        String dayKey = "rank:daily:" + LocalDate.now(); // ISO yyyy-MM-dd

        record(token, sid);

        // 总榜 +1
        assertThat(zscore("rank:total", sid)).isEqualTo(totalBefore + 1.0);
        // 日榜 +1(LocalDate.now().toString() 默认 ISO 格式,与 service DAY_FMT 一致)
        assertThat(zscore(dayKey, sid)).isEqualTo(1.0);
        // 周榜:周号格式用 keys 扫描定位本周 key,断言该 sid score = 1
        Set<String> weekKeys = redis.keys("rank:weekly:*");
        assertThat(weekKeys).isNotEmpty();
        String weekKey = weekKeys.iterator().next();
        assertThat(zscore(weekKey, sid)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("play_count 恒等于 play_record 真实行数(强一致)")
    void playCountEqualsRowCount() throws Exception {
        String alice = login("alice", "123456");
        String bob = login("bob", "123456");
        long sid = 5;

        record(alice, sid);
        record(bob, sid);

        // play_count(冗余字段,同步自增)必须等于明细真实行数
        assertThat(playCount(sid)).isEqualTo(rowCount(sid));
    }
}
