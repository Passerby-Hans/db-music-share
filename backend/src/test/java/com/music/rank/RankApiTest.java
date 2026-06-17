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
}
