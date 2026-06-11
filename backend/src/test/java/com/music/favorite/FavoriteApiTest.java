package com.music.favorite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 收藏模块 API 集成测试。
 *
 * <p>覆盖 {@code com.music.favorite} 的 4 个接口：收藏、取消收藏、我的收藏列表、
 * 收藏状态查询。基于 {@code 03_seed.sql} 的真实种子数据断言：</p>
 * <ul>
 *   <li>alice(uid=5) 种子收藏了 sid=2/7/4；carol(uid=7) 收藏了 sid=4/8 与已下架的 sid=12；</li>
 *   <li>sid=1「简单爱」可见、sid=9 待审、sid=12 软删（口径A 判定 playable）；</li>
 *   <li>账号：alice(uid=5)、bob(uid=6)、carol(uid=7)，明文密码均 123456。</li>
 * </ul>
 *
 * <p>每个用例由基类 {@code @Transactional} 保证结束回滚，不污染种子数据。
 * 鉴权走真实登录流程（POST /api/auth/login 拿 token，放入 X-Token 头）。</p>
 */
class FavoriteApiTest extends AbstractIntegrationTest {

    /** 会话令牌请求头名（与 AuthInterceptor.TOKEN_HEADER 一致）。 */
    private static final String TOKEN_HEADER = "X-Token";

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 走真实登录接口换取会话令牌。
     *
     * @param username 登录名
     * @param password 明文密码
     * @return 会话 token
     */
    private String login(String username, String password) throws Exception {
        String body = """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return readJson(res).path("data").path("token").asText();
    }

    /**
     * 解析响应体为 JSON 树。
     *
     * @param res MockMvc 执行结果
     * @return JSON 根节点
     */
    private JsonNode readJson(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    /** 查询某用户对某歌的收藏状态（true/false）。 */
    private boolean favStatus(String token, long sid) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/favorite/{sid}/status", sid)
                        .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(res).path("data").asBoolean();
    }

    /**
     * 在"我的收藏"列表里找指定 sid 的记录节点。
     *
     * @param token 会话令牌
     * @param sid   目标歌曲 sid
     * @return 找到返回节点，未找到返回 null
     */
    private JsonNode findInMine(String token, long sid) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/favorite/mine")
                        .header(TOKEN_HEADER, token)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode rec : readJson(res).path("data").path("records")) {
            if (rec.path("sid").asLong() == sid) {
                return rec;
            }
        }
        return null;
    }

    // ============================ 收藏 ============================

    @Test
    @DisplayName("收藏一首可见的歌：成功，状态变已收藏")
    void addFavorite() throws Exception {
        // bob(uid=6) 种子未收藏 sid=1，收藏之
        String token = login("bob", "123456");
        assertThat(favStatus(token, 1)).isFalse();

        mockMvc.perform(post("/api/favorite/{sid}", 1)
                        .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertThat(favStatus(token, 1)).isTrue();
    }

    @Test
    @DisplayName("重复收藏：幂等，返回成功且不报错")
    void addFavoriteIdempotent() throws Exception {
        String token = login("bob", "123456");
        mockMvc.perform(post("/api/favorite/{sid}", 1).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200));
        // 再收藏一次仍成功
        mockMvc.perform(post("/api/favorite/{sid}", 1).header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertThat(favStatus(token, 1)).isTrue();
    }

    @Test
    @DisplayName("收藏待审歌曲（sid=9，口径A不可见）：404")
    void addFavoriteOnPendingSong() throws Exception {
        String token = login("bob", "123456");
        mockMvc.perform(post("/api/favorite/{sid}", 9)
                        .header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("收藏软删歌曲（sid=12，已下架）：404")
    void addFavoriteOnDeletedSong() throws Exception {
        String token = login("bob", "123456");
        mockMvc.perform(post("/api/favorite/{sid}", 12)
                        .header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("收藏不存在的歌曲：404")
    void addFavoriteOnMissingSong() throws Exception {
        String token = login("bob", "123456");
        mockMvc.perform(post("/api/favorite/{sid}", 99999)
                        .header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("未登录收藏：401")
    void rejectAnonymousAdd() throws Exception {
        mockMvc.perform(post("/api/favorite/{sid}", 1))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================ 取消收藏 ============================

    @Test
    @DisplayName("取消已收藏的歌：成功，状态变未收藏")
    void removeFavorite() throws Exception {
        // alice(uid=5) 种子已收藏 sid=2
        String token = login("alice", "123456");
        assertThat(favStatus(token, 2)).isTrue();

        mockMvc.perform(delete("/api/favorite/{sid}", 2)
                        .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertThat(favStatus(token, 2)).isFalse();
    }

    @Test
    @DisplayName("取消未收藏的歌：幂等，返回成功")
    void removeFavoriteIdempotent() throws Exception {
        // bob 未收藏 sid=3
        String token = login("bob", "123456");
        mockMvc.perform(delete("/api/favorite/{sid}", 3)
                        .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("取消收藏已下架的歌：成功（失效歌也允许取消）")
    void removeFavoriteOnDeletedSong() throws Exception {
        // carol(uid=7) 种子收藏了已下架的 sid=12
        String token = login("carol", "123456");
        assertThat(favStatus(token, 12)).isTrue();
        mockMvc.perform(delete("/api/favorite/{sid}", 12)
                        .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertThat(favStatus(token, 12)).isFalse();
    }

    // ============================ 我的收藏列表 ============================

    @Test
    @DisplayName("我的收藏列表：含种子收藏，按收藏时间倒序")
    void listMine() throws Exception {
        // alice 种子收藏 sid=2(10天前)/7(8天前)/4(5天前)，倒序应 4,7,2
        String token = login("alice", "123456");
        MvcResult res = mockMvc.perform(get("/api/favorite/mine")
                        .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andReturn();
        JsonNode data = readJson(res).path("data");
        assertThat(data.path("total").asLong()).isGreaterThanOrEqualTo(3);
        // 倒序：第一条是最近收藏的 sid=4
        assertThat(data.path("records").get(0).path("sid").asLong()).isEqualTo(4L);
    }

    @Test
    @DisplayName("我的收藏列表：可见歌 playable=true")
    void listMinePlayableTrue() throws Exception {
        String token = login("alice", "123456");
        JsonNode rec = findInMine(token, 2);
        assertThat(rec).isNotNull();
        assertThat(rec.path("playable").asBoolean()).isTrue();
        // 附带歌曲信息字段
        assertThat(rec.path("title").asText()).isNotEmpty();
    }

    @Test
    @DisplayName("我的收藏列表：已下架歌仍展示但 playable=false")
    void listMineKeepsInvisibleSongAsNotPlayable() throws Exception {
        // carol 收藏了已下架的 sid=12，列表应仍含它且 playable=false
        String token = login("carol", "123456");
        JsonNode rec = findInMine(token, 12);
        assertThat(rec).as("已下架的收藏歌应仍出现在列表中").isNotNull();
        assertThat(rec.path("playable").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("未登录查我的收藏：401")
    void rejectAnonymousListMine() throws Exception {
        mockMvc.perform(get("/api/favorite/mine"))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================ 收藏状态查询 ============================

    @Test
    @DisplayName("收藏状态查询：已收藏返回 true、未收藏返回 false")
    void statusQuery() throws Exception {
        String token = login("alice", "123456");
        // 种子：alice 收藏了 sid=2，未收藏 sid=1
        assertThat(favStatus(token, 2)).isTrue();
        assertThat(favStatus(token, 1)).isFalse();
    }

    @Test
    @DisplayName("未登录查收藏状态：401")
    void rejectAnonymousStatus() throws Exception {
        mockMvc.perform(get("/api/favorite/{sid}/status", 1))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================ 分支与边界补充 ============================

    @Test
    @DisplayName("收藏已驳回的歌(sid=11)：404")
    void addRejectedSong() throws Exception {
        // sid=11 audit_status=2 驳回——与待审/软删同抛错语义，独立坐实
        String token = login("alice", "123456");
        mockMvc.perform(post("/api/favorite/{sid}", 11).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("未登录取消收藏：401（DELETE 的鉴权分支）")
    void rejectAnonymousRemove() throws Exception {
        mockMvc.perform(delete("/api/favorite/{sid}", 1))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("我的收藏分页翻页 + 跨页仍倒序")
    void listMinePaging() throws Exception {
        // alice 收藏 sid=2/7/4，fav_time 倒序为 4→7→2
        String token = login("alice", "123456");
        MvcResult p1 = mockMvc.perform(get("/api/favorite/mine")
                        .header(TOKEN_HEADER, token).param("page", "1").param("size", "1"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode d1 = readJson(p1).path("data");
        assertThat(d1.path("records").get(0).path("sid").asLong()).isEqualTo(4);
        assertThat(d1.path("size").asLong()).isEqualTo(1);

        MvcResult p2 = mockMvc.perform(get("/api/favorite/mine")
                        .header(TOKEN_HEADER, token).param("page", "2").param("size", "1"))
                .andReturn();
        // 第二页应是倒序的第二条 sid=7
        assertThat(readJson(p2).path("data").path("records").get(0).path("sid").asLong()).isEqualTo(7);
    }

    @Test
    @DisplayName("无任何收藏的用户：空列表 total=0")
    void listMineEmpty() throws Exception {
        // admin(uid=1) 种子无收藏
        String token = login("admin", "123456");
        MvcResult res = mockMvc.perform(get("/api/favorite/mine").header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = readJson(res).path("data");
        assertThat(data.path("total").asLong()).isZero();
        assertThat(data.path("records")).isEmpty();
    }

    @Test
    @DisplayName("同一列表内 playable 真假混合：carol 的 sid=12(false) 与 sid=8/4(true)")
    void listMinePlayableMixed() throws Exception {
        // carol(uid=7) 收藏 sid=4/8(可见) 与 sid=12(已软删)
        String token = login("carol", "123456");
        MvcResult res = mockMvc.perform(get("/api/favorite/mine")
                        .header(TOKEN_HEADER, token).param("size", "100"))
                .andReturn();
        boolean sawTrue = false, sawFalse = false;
        for (JsonNode rec : readJson(res).path("data").path("records")) {
            long sid = rec.path("sid").asLong();
            if (sid == 12L) {
                assertThat(rec.path("playable").asBoolean()).isFalse();
                sawFalse = true;
            } else if (sid == 8L || sid == 4L) {
                assertThat(rec.path("playable").asBoolean()).isTrue();
                sawTrue = true;
            }
        }
        // 同一响应里真假都出现
        assertThat(sawTrue).isTrue();
        assertThat(sawFalse).isTrue();
    }

    @Test
    @DisplayName("重复收藏真幂等：连收两次 total 只 +1（不重复插行）")
    void addFavoriteTrulyIdempotent() throws Exception {
        // bob(uid=6) 未收藏 sid=1
        String token = login("bob", "123456");
        long before = readJson(mockMvc.perform(get("/api/favorite/mine")
                        .header(TOKEN_HEADER, token).param("size", "100"))
                .andReturn()).path("data").path("total").asLong();
        // 连收两次
        mockMvc.perform(post("/api/favorite/{sid}", 1).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/api/favorite/{sid}", 1).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200));
        long after = readJson(mockMvc.perform(get("/api/favorite/mine")
                        .header(TOKEN_HEADER, token).param("size", "100"))
                .andReturn()).path("data").path("total").asLong();
        assertThat(after).isEqualTo(before + 1);
    }
}
