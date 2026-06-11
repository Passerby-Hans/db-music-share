package com.music.rating;

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
 * 评分模块 API 集成测试（{@code /api/rating}）。
 *
 * <p>覆盖提交评分（upsert：首评/改分）、撤销（幂等）、评分概况（软鉴权 + 平均分/人数/
 * 我的评分）、我的评分分页（含失效歌 playable=false）、score 1~5 校验、可见性 404、
 * 鉴权 401。</p>
 *
 * <p>种子（03_seed.sql）评分数据：sid=2 由 uid5/6/7/8 评 5/5/4/5（均分 4.75，4 人）；
 * sid=1 由 uid5/6 评 5/4（均分 4.5，2 人）；alice(uid=5) 评过 sid=1/2/4/5/7 共 5 首。
 * 歌曲 sid=1~8 多为已通过、sid=9/10 待审、sid=11 驳回、sid=12 软删。密码均 123456。</p>
 */
class RatingApiTest extends AbstractIntegrationTest {

    private static final String TOKEN_HEADER = "X-Token";

    @Autowired
    private ObjectMapper objectMapper;

    private String login(String username, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return readJson(res).path("data").path("token").asText();
    }

    private JsonNode readJson(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    /** 提交评分。 */
    private void rate(String token, long sid, int score) throws Exception {
        mockMvc.perform(post("/api/rating/{sid}", sid)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":" + score + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /** 取某歌评分概况节点（带 token 则回填 myScore）。 */
    private JsonNode stat(String token, long sid) throws Exception {
        var req = get("/api/rating/{sid}", sid);
        if (token != null) {
            req = req.header(TOKEN_HEADER, token);
        }
        MvcResult res = mockMvc.perform(req).andReturn();
        return readJson(res).path("data");
    }

    // ============================ 提交评分（upsert） ============================

    @Test
    @DisplayName("首次评分：成功，概况里我的评分回填")
    void rateFirstTime() throws Exception {
        // bob(uid=6) 此前未评 sid=3，首评给 4 分
        String token = login("bob", "123456");
        rate(token, 3, 4);
        assertThat(stat(token, 3).path("myScore").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("再次评分=改分：覆盖而非新增，人数不变、我的分更新")
    void rateAgainOverwrites() throws Exception {
        // alice(uid=5) 种子已评 sid=2 为 5 分；sid=2 共 4 人评
        String token = login("alice", "123456");
        long countBefore = stat(token, 2).path("ratingCount").asLong();
        assertThat(countBefore).isEqualTo(4);

        // 改成 3 分
        rate(token, 2, 3);
        JsonNode after = stat(token, 2);
        // 人数不变（改分不新增记录）
        assertThat(after.path("ratingCount").asLong()).isEqualTo(4);
        // 我的评分已更新为 3
        assertThat(after.path("myScore").asInt()).isEqualTo(3);
        // 平均分随之变化：原 5/5/4/5=4.75，alice 改 5→3 后 3/5/4/5=4.25→4.3
        assertThat(after.path("avgScore").asDouble()).isEqualTo(4.3);
    }

    @Test
    @DisplayName("评分低于 1：400 参数校验")
    void rateScoreTooLow() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(post("/api/rating/{sid}", 1)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"score":0}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("评分高于 5：400 参数校验")
    void rateScoreTooHigh() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(post("/api/rating/{sid}", 1)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"score":6}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("评分合法边界 1 和 5：均成功（端点不被误拒）")
    void rateBoundaryScores() throws Exception {
        String token = login("bob", "123456");
        // 下端点 1：给 bob 未评过的 sid=4 打 1 分
        rate(token, 4, 1);
        assertThat(stat(token, 4).path("myScore").asInt()).isEqualTo(1);
        // 上端点 5：改成 5 分
        rate(token, 4, 5);
        assertThat(stat(token, 4).path("myScore").asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("评分缺 score 字段：400 参数校验")
    void rateMissingScore() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(post("/api/rating/{sid}", 1)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("给待审歌评分：404（仅能评口径A可见的歌）")
    void ratePendingSong() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(post("/api/rating/{sid}", 9)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"score":5}
                                """))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("给已软删歌评分：404")
    void rateDeletedSong() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(post("/api/rating/{sid}", 12)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"score":5}
                                """))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("给已驳回歌评分：404")
    void rateRejectedSong() throws Exception {
        // sid=11 audit_status=2 驳回
        String token = login("alice", "123456");
        mockMvc.perform(post("/api/rating/{sid}", 11)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"score":5}
                                """))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("给不存在的歌评分：404")
    void rateMissingSong() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(post("/api/rating/{sid}", 99999)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"score":5}
                                """))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("未登录评分：401")
    void rateAnonymous() throws Exception {
        mockMvc.perform(post("/api/rating/{sid}", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"score":5}
                                """))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================ 撤销评分 ============================

    @Test
    @DisplayName("撤销评分：成功，概况人数-1、我的评分变 null")
    void cancelRating() throws Exception {
        // alice(uid=5) 种子已评 sid=2；sid=2 共 4 人
        String token = login("alice", "123456");
        mockMvc.perform(delete("/api/rating/{sid}", 2).header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        JsonNode after = stat(token, 2);
        assertThat(after.path("ratingCount").asLong()).isEqualTo(3);
        // 撤销后我的评分为 null（JSON null）
        assertThat(after.path("myScore").isNull()).isTrue();
    }

    @Test
    @DisplayName("撤销未评过的评分：幂等成功")
    void cancelNotRatedIdempotent() throws Exception {
        // bob(uid=6) 未评 sid=3
        String token = login("bob", "123456");
        mockMvc.perform(delete("/api/rating/{sid}", 3).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("撤销已下架歌的评分：成功（不校验可见性）")
    void cancelOnDeletedSong() throws Exception {
        // 先让 alice 撤 sid=12（已软删）的评分——本就没有，幂等成功即可
        String token = login("alice", "123456");
        mockMvc.perform(delete("/api/rating/{sid}", 12).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("未登录撤销评分：401")
    void cancelAnonymous() throws Exception {
        mockMvc.perform(delete("/api/rating/{sid}", 1))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================ 评分概况（软鉴权） ============================

    @Test
    @DisplayName("游客查评分概况：返回平均分与人数，myScore 为 null")
    void guestViewStat() throws Exception {
        // sid=2：4 人评 5/5/4/5，均分 4.75
        JsonNode data = stat(null, 2);
        assertThat(data.path("sid").asLong()).isEqualTo(2);
        assertThat(data.path("ratingCount").asLong()).isEqualTo(4);
        assertThat(data.path("avgScore").asDouble()).isEqualTo(4.8); // 4.75 四舍五入一位小数
        assertThat(data.path("myScore").isNull()).isTrue();
    }

    @Test
    @DisplayName("登录用户查评分概况：回填我的评分")
    void loginViewStatWithMyScore() throws Exception {
        // alice(uid=5) 对 sid=2 评了 5 分
        String token = login("alice", "123456");
        JsonNode data = stat(token, 2);
        assertThat(data.path("myScore").asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("登录用户查未评过的歌：myScore 为 null，仍返回均分人数")
    void loginViewStatNotRated() throws Exception {
        // sid=3 种子里被 uid8 评过 2 分（1 人）；alice 没评过 sid=3
        String token = login("alice", "123456");
        JsonNode data = stat(token, 3);
        assertThat(data.path("ratingCount").asLong()).isEqualTo(1);
        assertThat(data.path("myScore").isNull()).isTrue();
    }

    @Test
    @DisplayName("查无人评分的歌：均分 0、人数 0")
    void statNoRatings() throws Exception {
        // sid=6 种子里无人评分
        JsonNode data = stat(null, 6);
        assertThat(data.path("ratingCount").asLong()).isZero();
        assertThat(data.path("avgScore").asDouble()).isZero();
    }

    @Test
    @DisplayName("带失效令牌查概况：401（软鉴权不静默降级）")
    void statWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/rating/{sid}", 2).header(TOKEN_HEADER, "garbage-token-xxx"))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================ 我的评分列表 ============================

    @Test
    @DisplayName("我的评分：返回本人评过的歌（含分数），按时间倒序")
    void listMine() throws Exception {
        // alice(uid=5) 评过 sid=1/2/4/5/7 共 5 首
        String token = login("alice", "123456");
        MvcResult res = mockMvc.perform(get("/api/rating/mine")
                        .header(TOKEN_HEADER, token).param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = readJson(res).path("data");
        assertThat(data.path("total").asLong()).isEqualTo(5);
        // 每项都带 score（1~5）
        for (JsonNode r : data.path("records")) {
            int score = r.path("score").asInt();
            assertThat(score).isBetween(1, 5);
        }
    }

    @Test
    @DisplayName("我的评分：未登录→401")
    void listMineRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/rating/mine"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("我的评分含已下架歌：不剔除，标 playable=false")
    void listMineKeepsUnplayable() throws Exception {
        // carol(uid=7) 种子评过 sid=12(已软删) 与 sid=2/4/8(正常)
        String token = login("carol", "123456");
        MvcResult res = mockMvc.perform(get("/api/rating/mine")
                        .header(TOKEN_HEADER, token).param("size", "20"))
                .andReturn();
        JsonNode records = readJson(res).path("data").path("records");
        boolean foundUnplayable = false;
        for (JsonNode r : records) {
            if (r.path("sid").asLong() == 12L) {
                // 已下架的歌仍在"我的评分"中，但不可播放，且分数如实保留
                assertThat(r.path("playable").asBoolean()).isFalse();
                assertThat(r.path("score").asInt()).isEqualTo(3);
                foundUnplayable = true;
            } else {
                assertThat(r.path("playable").asBoolean()).isTrue();
            }
        }
        assertThat(foundUnplayable).isTrue();
    }

    // ============================ 组合场景 ============================

    @Test
    @DisplayName("撤销后可重新评分：先删再评，新分生效")
    void reRateAfterCancel() throws Exception {
        // alice 种子评过 sid=1 为 5 分
        String token = login("alice", "123456");
        mockMvc.perform(delete("/api/rating/{sid}", 1).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200));
        assertThat(stat(token, 1).path("myScore").isNull()).isTrue();
        // 重新评 2 分
        rate(token, 1, 2);
        assertThat(stat(token, 1).path("myScore").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("评分全部撤光后：概况归零（count=0, avg=0）")
    void statZeroAfterAllCancelled() throws Exception {
        // sid=1 种子有 alice(uid5,5分) 与 bob(uid6,4分) 两人
        String alice = login("alice", "123456");
        String bob = login("bob", "123456");
        mockMvc.perform(delete("/api/rating/{sid}", 1).header(TOKEN_HEADER, alice))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(delete("/api/rating/{sid}", 1).header(TOKEN_HEADER, bob))
                .andExpect(jsonPath("$.code").value(200));
        JsonNode data = stat(null, 1);
        assertThat(data.path("ratingCount").asLong()).isZero();
        assertThat(data.path("avgScore").asDouble()).isZero();
    }
}
