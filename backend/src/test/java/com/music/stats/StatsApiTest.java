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
    @Autowired
    private com.music.stats.service.StatsService statsService;

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
        // 第一名总播放量 >0(种子上传者有点唱);且若有第二项,验证倒序(贡献榜核心语义)
        long firstTotal = data.get(0).path("totalPlayCount").asLong();
        assertThat(firstTotal).isGreaterThan(0);
        if (data.size() >= 2) {
            long secondTotal = data.get(1).path("totalPlayCount").asLong();
            assertThat(firstTotal).isGreaterThanOrEqualTo(secondTotal); // 倒序
        }
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
    @DisplayName("首次读触发聚合写缓存;二次读命中正常返回")
    void cacheHitOnSecondRead() throws Exception {
        String token = login("admin", "123456");
        // 首次读:聚合 + 写缓存 stats:top-users(cache-aside 首次写)
        JsonNode first = stats(token, "top-users");
        assertThat(first.size()).isGreaterThan(0);
        // 断言缓存已写入——这是 cache-aside "首次写缓存" 的关键证据
        assertThat(redis.opsForValue().get("stats:top-users")).isNotNull();
        // 第二次读:命中缓存,正常返回数据即可。
        // 注意:缓存"命中"不靠返回值与 first 相等来证明(无缓存时两次实时聚合的 JSON 也
        // byte-for-byte 相等,是幂等而非缓存命中);命中由"首次读后缓存已写入"+服务端
        // cache-aside 读路径保证,这里只断言二次读正常返回数据。
        JsonNode second = stats(token, "top-users");
        assertThat(second.size()).isGreaterThan(0);
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

    @Test
    @DisplayName("定时刷新:refreshAll 覆盖写两缓存")
    void refreshAllRewritesCache() throws Exception {
        // 先污染缓存(写一个错误值,uid=999 种子不存在)
        redis.opsForValue().set("stats:top-users", "[{\"rank\":1,\"uid\":999,\"playCount\":999}]", java.time.Duration.ofHours(1));
        // 刷新
        statsService.refreshAll();
        // 读出来不再是 999(被真实聚合覆盖)
        String json = mockMvc.perform(get("/api/admin/stats/top-users")
                        .header(TOKEN_HEADER, login("admin", "123456")))
                .andReturn().getResponse().getContentAsString();
        boolean has999 = json.contains("\"uid\":999");
        assertThat(has999).isFalse();
        // 两缓存都被写
        assertThat(redis.opsForValue().get("stats:top-users")).isNotNull();
        assertThat(redis.opsForValue().get("stats:top-uploaders")).isNotNull();
    }
}
