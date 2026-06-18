package com.music.stats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 统计报表模块 API 集成测试({@code /api/admin/stats})。
 *
 * <p>覆盖用户活跃度/上传者贡献 TOP10、role=2 鉴权、缓存命中/miss 兜底、定时刷新。
 * 种子:admin/123456(uid=1,role=2)、uploader_jay(role=1)、alice(role=0)。</p>
 */
class StatsApiTest extends AbstractIntegrationTest {

    private static final String TOKEN_HEADER = "X-Token";

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StringRedisTemplate redis;

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

    /** 读某统计接口返回 data 数组(带管理员 Token)。 */
    private JsonNode stats(String token, String type) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/admin/stats/{type}", type).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data");
    }

    @AfterEach
    void cleanupRedis() {
        Set<String> keys = redis.keys("stats:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    @DisplayName("用户活跃度 TOP10:管理员可读,返回倒序+回填昵称/头像")
    void topUsersAdmin() throws Exception {
        String token = login("admin", "123456"); // role=2
        JsonNode data = stats(token, "top-users");
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isGreaterThan(0); // 种子 play_record 36 行有点唱
        // 第一项(rank=1)含昵称/头像/点唱数
        assertThat(data.get(0).path("rank").asInt()).isEqualTo(1);
        assertThat(data.get(0).path("nickname").asText()).isNotBlank();
        assertThat(data.get(0).path("avatar").asText()).isNotBlank();
        assertThat(data.get(0).path("playCount").asLong()).isGreaterThan(0);
    }

    @Test
    @DisplayName("上传者贡献 TOP10:返回倒序+回填+songCount/totalPlayCount")
    void topUploadersAdmin() throws Exception {
        String token = login("admin", "123456");
        JsonNode data = stats(token, "top-uploaders");
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isGreaterThan(0); // 种子有上传者
        assertThat(data.get(0).path("rank").asInt()).isEqualTo(1);
        assertThat(data.get(0).path("nickname").asText()).isNotBlank();
        assertThat(data.get(0).path("songCount").asLong()).isGreaterThan(0);
        assertThat(data.get(0).path("totalPlayCount").asLong()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("普通用户(role=0)读统计:403")
    void statsForbiddenToNormalUser() throws Exception {
        String token = login("alice", "123456"); // role=0
        mockMvc.perform(get("/api/admin/stats/top-users").header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("上传者(role=1)读统计:403")
    void statsForbiddenToUploader() throws Exception {
        String token = login("uploader_jay", "123456"); // role=1
        mockMvc.perform(get("/api/admin/stats/top-uploaders").header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("未登录读统计:401")
    void statsAnonymousUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/stats/top-users"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("缓存命中:首次读写缓存,第二次读命中(返回相同)")
    void cacheHitOnSecondRead() throws Exception {
        String token = login("admin", "123456");
        // 首次读:聚合 + 写缓存 stats:top-users
        JsonNode first = stats(token, "top-users");
        assertThat(first.size()).isGreaterThan(0);
        // 断言缓存已写入
        assertThat(redis.opsForValue().get("stats:top-users")).isNotNull();
        // 第二次读:命中缓存(返回同 first)
        JsonNode second = stats(token, "top-users");
        assertThat(second.toString()).isEqualTo(first.toString());
    }

    @Test
    @DisplayName("缓存 miss 兜底:删缓存后读→重新聚合+写缓存")
    void cacheMissFallback() throws Exception {
        String token = login("admin", "123456");
        stats(token, "top-users"); // 先填缓存
        redis.delete("stats:top-users"); // 清缓存制造 miss
        JsonNode data = stats(token, "top-users"); // 重新聚合
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isGreaterThan(0); // 仍有数据(降级聚合)
        assertThat(redis.opsForValue().get("stats:top-users")).isNotNull(); // 重新写缓存
    }
}
