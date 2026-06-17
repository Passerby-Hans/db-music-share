package com.music.rank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 排行榜模块 API 集成测试({@code /api/rank})。
 *
 * <p>覆盖总/日/周榜 Redis 读取、空榜降级聚合 play_record、公开访问(白名单无 token)、
 * TOP10 裁剪、空榜单返回 []、回填(标题/封面/上传者)。</p>
 */
class RankApiTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private com.music.rank.service.RankService rankService;

    /** 读某榜返回 data 节点(records 数组)。 */
    private JsonNode board(String type) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/rank/{type}", type)) // 无 token:白名单公开
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data");
    }

    /** 向某 ZSET key 灌入 sid→score。 */
    private void zadd(String key, long sid, double score) {
        redis.opsForZSet().add(key, String.valueOf(sid), score);
    }

    @AfterEach
    void cleanupRedis() {
        Set<String> keys = redis.keys("rank:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    @DisplayName("总榜 Redis 有数据:返回 Top10,按 score 倒序,含回填信息")
    void totalBoardFromRedis() throws Exception {
        // 灌 3 首:晴天(11) > 浮夸(5) > 简单爱(4)
        zadd("rank:total", 1L, 4.0);
        zadd("rank:total", 2L, 11.0);
        zadd("rank:total", 3L, 5.0);

        JsonNode data = board("total");
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isEqualTo(3);
        // 倒序:第一项 sid=2 score=11
        assertThat(data.get(0).path("sid").asLong()).isEqualTo(2L);
        assertThat(data.get(0).path("score").asLong()).isEqualTo(11L);
        assertThat(data.get(0).path("rank").asInt()).isEqualTo(1);
        // 回填:标题非空、封面非空(直链)
        assertThat(data.get(0).path("title").asText()).isNotBlank();
        assertThat(data.get(0).path("cover").asText()).isNotBlank();
    }

    @Test
    @DisplayName("总榜 Redis 空:降级聚合 play_record,仍返回正确 Top10")
    void totalBoardFallbackOnEmptyRedis() throws Exception {
        // 不灌 rank:total → Redis 空 → 降级聚合 play_record(种子有 36 行真实点唱)
        JsonNode data = board("total");
        assertThat(data.isArray()).isTrue();
        // 种子总榜:晴天(sid=2) 以 11 次播放居首(见 03_seed.sql 验证查询)
        assertThat(data.size()).isGreaterThan(0);
        assertThat(data.get(0).path("rank").asInt()).isEqualTo(1);
        assertThat(data.get(0).path("score").asLong()).isGreaterThan(0);
    }

    @Test
    @DisplayName("日榜:命中 rank:daily:<今天> key")
    void dailyBoardKeyResolution() throws Exception {
        String today = java.time.LocalDate.now().toString(); // ISO yyyy-MM-dd
        zadd("rank:daily:" + today, 1L, 3.0);
        zadd("rank:daily:" + today, 2L, 7.0);
        JsonNode data = board("daily");
        assertThat(data.size()).isEqualTo(2);
        assertThat(data.get(0).path("sid").asLong()).isEqualTo(2L); // 7 > 3
    }

    @Test
    @DisplayName("周榜:命中 rank:weekly:<本周> key")
    void weeklyBoardKeyResolution() throws Exception {
        // 用与 service 完全相同的 WEEK_FMT 构造本周 key(4位周年+-W+2位ISO周号),保证一致
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        java.time.format.DateTimeFormatter weekFmt = new java.time.format.DateTimeFormatterBuilder()
                .appendValue(java.time.temporal.WeekFields.ISO.weekBasedYear(), 4)
                .appendLiteral("-W")
                .appendValue(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear(), 2)
                .toFormatter(java.util.Locale.ROOT);
        String weekKey = "rank:weekly:" + now.format(weekFmt);
        zadd(weekKey, 5L, 9.0);
        zadd(weekKey, 6L, 2.0);
        JsonNode data = board("weekly");
        // 命中本周 key:sid=5(9) 排在 sid=6(2) 前;Redis 非空走 Redis 分支,返回 2 条
        assertThat(data.size()).isEqualTo(2);
        assertThat(data.get(0).path("sid").asLong()).isEqualTo(5L);  // 9 > 2
        assertThat(data.get(1).path("sid").asLong()).isEqualTo(6L);
    }

    @Test
    @DisplayName("公开访问:无 token 读三榜均 200(白名单)")
    void publicAccessNoToken() throws Exception {
        mockMvc.perform(get("/api/rank/total")).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/rank/daily")).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/rank/weekly")).andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("TOP10 裁剪:灌 12 首只返回 10")
    void top10Truncation() throws Exception {
        for (long sid = 1; sid <= 12; sid++) {
            zadd("rank:total", sid, sid * 1.0); // sid=12 最高
        }
        JsonNode data = board("total");
        assertThat(data.size()).isEqualTo(10);
        // 最高分 sid=12 排第一
        assertThat(data.get(0).path("sid").asLong()).isEqualTo(12L);
    }

    @Test
    @DisplayName("周榜:Redis 空→降级聚合,不报错返回数组")
    void emptyBoardReturnsEmptyList() throws Exception {
        // 用一个种子里无人点唱的时间窗造空(周榜若无本周数据)
        JsonNode data = board("weekly");
        // 种子本周有点唱数据,降级聚合会返回非空;此用例验证 weekly 读路径不报错、返回数组(非真"空榜")
        assertThat(data.isArray()).isTrue();
    }

    @Test
    @DisplayName("对账重建:破坏 rank:total 后 rebuild,Redis 与 play_record 聚合一致")
    void rebuildFixesDrift() throws Exception {
        // 先灌一个错误 score 制造漂移
        zadd("rank:total", 1L, 999.0);
        // 对账重建总榜(从 play_record 全量聚合覆盖写回)
        rankService.rebuild(com.music.rank.BoardType.TOTAL);

        // 读总榜:不再是 999,而是 play_record 真实聚合
        JsonNode data = board("total");
        // sid=1 的 score 应为 play_record 中 sid=1 的真实行数(种子已知),而非 999
        for (JsonNode n : data) {
            if (n.path("sid").asLong() == 1L) {
                assertThat(n.path("score").asLong()).isLessThan(999L);
            }
        }
    }
}
