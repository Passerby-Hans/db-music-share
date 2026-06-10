package com.music.comment;

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
 * 评论模块 API 集成测试。
 *
 * <p>覆盖 {@code com.music.comment} 的接口：发表/回复、按歌查主评论、
 * 查回复、我的评论、删除、点赞/取消点赞。基于 {@code 03_seed.sql} 的真实种子数据断言：</p>
 * <ul>
 *   <li>sid=2「晴天」：主评论 cid=1、cid=2；cid=1 下有回复 cid=3、cid=4；</li>
 *   <li>点赞种子：cid=1 被 uid=5/6/7/8 共 4 人赞、cid=2 被 uid=5/7 赞；</li>
 *   <li>sid=9 待审、sid=12 软删，均不可评论（口径A 不可见）；</li>
 *   <li>账号：admin(uid=1,role=2)、alice(uid=5,role=0)、bob(uid=6,role=0)，明文密码均 123456。</li>
 * </ul>
 *
 * <p>每个用例由基类 {@code @Transactional} 保证结束回滚，不污染种子数据。
 * 鉴权走真实登录流程（POST /api/auth/login 拿 token，放入 X-Token 头）。</p>
 */
class CommentApiTest extends AbstractIntegrationTest {

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

    /**
     * 发表评论/回复，返回新评论 cid。
     *
     * @param token     会话令牌
     * @param sid       歌曲 sid
     * @param content   正文
     * @param parentCid 父评论 cid，可空（主评论传 null）
     * @return 新评论 cid
     */
    private long createComment(String token, long sid, String content, Long parentCid) throws Exception {
        String body = parentCid == null
                ? """
                  {"sid":%d,"content":"%s"}
                  """.formatted(sid, content)
                : """
                  {"sid":%d,"content":"%s","parentCid":%d}
                  """.formatted(sid, content, parentCid);
        MvcResult res = mockMvc.perform(post("/api/comment")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return readJson(res).path("data").asLong();
    }

    // ============================ 公开查看 ============================

    @Test
    @DisplayName("游客查某歌主评论分页：sid=2 应有≥2条主评论且带 replyCount")
    void guestListSongComments() throws Exception {
        // 不带 token，验证公开接口放行
        MvcResult res = mockMvc.perform(get("/api/comment/song/{sid}", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andReturn();
        JsonNode data = readJson(res).path("data");
        // 种子：sid=2 有 cid=1、cid=2 两条主评论
        assertThat(data.path("total").asLong()).isGreaterThanOrEqualTo(2);
        // 每条主评论都应带 replyCount 字段
        for (JsonNode rec : data.path("records")) {
            assertThat(rec.has("replyCount")).isTrue();
        }
        // cid=1 这条主评论种子下挂了 2 条回复
        boolean foundCid1WithReplies = false;
        for (JsonNode rec : data.path("records")) {
            if (rec.path("cid").asLong() == 1L) {
                assertThat(rec.path("replyCount").asLong()).isGreaterThanOrEqualTo(2);
                foundCid1WithReplies = true;
            }
        }
        assertThat(foundCid1WithReplies).isTrue();
    }

    @Test
    @DisplayName("游客查某主评论的回复分页：cid=1 应有≥2条回复")
    void guestListReplies() throws Exception {
        mockMvc.perform(get("/api/comment/{cid}/replies", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    // ============================ 发表评论 ============================

    @Test
    @DisplayName("登录用户发表主评论：返回新 cid")
    void createTopLevelComment() throws Exception {
        String token = login("alice", "123456");
        long cid = createComment(token, 1, "简单爱测试评论", null);
        assertThat(cid).isPositive();
    }

    @Test
    @DisplayName("登录用户回复主评论：两层盖楼成功")
    void replyToTopLevelComment() throws Exception {
        String token = login("alice", "123456");
        long parent = createComment(token, 1, "父评论", null);
        long reply = createComment(token, 1, "回复父评论", parent);
        assertThat(reply).isPositive();
        // 校验该回复出现在父评论的回复列表中
        mockMvc.perform(get("/api/comment/{cid}/replies", parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].parentCid").value((int) parent));
    }

    @Test
    @DisplayName("对回复再回复（第三层）：拒绝，仅支持两层")
    void rejectThirdLevelReply() throws Exception {
        String token = login("alice", "123456");
        long parent = createComment(token, 1, "主评论", null);
        long reply = createComment(token, 1, "一层回复", parent);
        // 以"回复"作为 parentCid 再发 → 400
        String body = """
                {"sid":1,"content":"想盖第三层","parentCid":%d}
                """.formatted(reply);
        mockMvc.perform(post("/api/comment")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("评论不存在的歌曲：404")
    void commentOnMissingSong() throws Exception {
        String token = login("alice", "123456");
        String body = """
                {"sid":99999,"content":"幽灵歌曲"}
                """;
        mockMvc.perform(post("/api/comment")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("评论待审歌曲（sid=9，口径A不可见）：404")
    void commentOnPendingSong() throws Exception {
        String token = login("alice", "123456");
        String body = """
                {"sid":9,"content":"待审歌曲不该能评论"}
                """;
        mockMvc.perform(post("/api/comment")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("空内容发评论：400 参数校验")
    void rejectBlankContent() throws Exception {
        String token = login("alice", "123456");
        String body = """
                {"sid":1,"content":"   "}
                """;
        mockMvc.perform(post("/api/comment")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("未登录发评论：401")
    void rejectAnonymousCreate() throws Exception {
        String body = """
                {"sid":1,"content":"游客不能评论"}
                """;
        mockMvc.perform(post("/api/comment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================ 我的评论 ============================

    @Test
    @DisplayName("我的评论分页：含本人刚发表的评论")
    void listMyComments() throws Exception {
        String token = login("alice", "123456");
        long cid = createComment(token, 1, "alice 的可见评论", null);
        MvcResult res = mockMvc.perform(get("/api/comment/mine")
                        .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode records = readJson(res).path("data").path("records");
        boolean found = false;
        for (JsonNode rec : records) {
            if (rec.path("cid").asLong() == cid) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    // ============================ 删除评论 ============================

    @Test
    @DisplayName("作者删除自己的评论：成功")
    void deleteOwnComment() throws Exception {
        String token = login("alice", "123456");
        long cid = createComment(token, 1, "待会儿自己删", null);
        mockMvc.perform(delete("/api/comment/{cid}", cid)
                        .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("删除他人评论（非管理员）：403")
    void rejectDeleteOthersComment() throws Exception {
        // alice 先发一条，模拟"他人评论"由另一个普通用户去删
        String aliceToken = login("alice", "123456");
        long cid = createComment(aliceToken, 1, "alice 的评论", null);
        // bob（uid=6, role=0 普通用户）尝试删 alice 的评论
        String bobToken = login("bob", "123456");
        mockMvc.perform(delete("/api/comment/{cid}", cid)
                        .header(TOKEN_HEADER, bobToken))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("管理员删除任意用户的评论：成功")
    void adminDeleteAnyComment() throws Exception {
        // alice 发一条，admin 删之
        String aliceToken = login("alice", "123456");
        long cid = createComment(aliceToken, 1, "等待被管理员删除", null);
        String adminToken = login("admin", "123456");
        mockMvc.perform(delete("/api/comment/{cid}", cid)
                        .header(TOKEN_HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("删除不存在的评论：404")
    void deleteMissingComment() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(delete("/api/comment/{cid}", 999999)
                        .header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    // ============================ 评论点赞 ============================

    @Test
    @DisplayName("点赞评论后：列表点赞数+1 且 likedByMe=true")
    void likeComment() throws Exception {
        // bob 对 cid=2（晴天主评论，种子里 bob 未点过）点赞
        String token = login("bob", "123456");
        long before = likeCountOf(token, 2L, 2L);

        mockMvc.perform(post("/api/comment/{cid}/like", 2)
                        .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 再查 sid=2 主评论列表，cid=2 点赞数应 +1 且 likedByMe=true
        JsonNode rec = findRecord(token, 2L, 2L);
        assertThat(rec.path("likeCount").asLong()).isEqualTo(before + 1);
        assertThat(rec.path("likedByMe").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("重复点赞：幂等，点赞数不重复累加")
    void likeIsIdempotent() throws Exception {
        String token = login("bob", "123456");
        // 连点两次
        like(token, 2L);
        long after1 = likeCountOf(token, 2L, 2L);
        like(token, 2L);
        long after2 = likeCountOf(token, 2L, 2L);
        assertThat(after2).isEqualTo(after1);
    }

    @Test
    @DisplayName("取消点赞后：点赞数-1 且 likedByMe=false")
    void unlikeComment() throws Exception {
        // 种子：uid=6(bob) 点过 cid=1。先确认已赞，取消后归零变化
        String token = login("bob", "123456");
        JsonNode before = findRecord(token, 2L, 1L);
        assertThat(before.path("likedByMe").asBoolean()).isTrue();
        long beforeCount = before.path("likeCount").asLong();

        mockMvc.perform(delete("/api/comment/{cid}/like", 1)
                        .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        JsonNode after = findRecord(token, 2L, 1L);
        assertThat(after.path("likeCount").asLong()).isEqualTo(beforeCount - 1);
        assertThat(after.path("likedByMe").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("取消未点赞的评论：幂等，返回成功")
    void unlikeWithoutLikeIsIdempotent() throws Exception {
        // alice(uid=5) 种子里未点过 cid=10
        String token = login("alice", "123456");
        mockMvc.perform(delete("/api/comment/{cid}/like", 10)
                        .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("点赞不存在的评论：404")
    void likeMissingComment() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(post("/api/comment/{cid}/like", 999999)
                        .header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("未登录点赞：401")
    void rejectAnonymousLike() throws Exception {
        mockMvc.perform(post("/api/comment/{cid}/like", 1))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("游客查评论列表：likedByMe 恒为 false（无 token 不报错）")
    void guestSeesLikedByMeFalse() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/comment/song/{sid}", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        for (JsonNode rec : readJson(res).path("data").path("records")) {
            // 游客视角：每条都带 likedByMe 字段且为 false
            assertThat(rec.has("likedByMe")).isTrue();
            assertThat(rec.path("likedByMe").asBoolean()).isFalse();
            // 点赞数是真实统计值（非游客身份相关），cid=1 种子有 4 个赞
            if (rec.path("cid").asLong() == 1L) {
                assertThat(rec.path("likeCount").asLong()).isGreaterThanOrEqualTo(4);
            }
        }
    }

    @Test
    @DisplayName("带失效 token 查评论列表（软鉴权）：401 提示重新登录")
    void staleTokenOnOptionalAuthRejected() throws Exception {
        // 伪造一个不存在的 sessionId：软鉴权下带了 token 但无效 → 401
        mockMvc.perform(get("/api/comment/song/{sid}", 2)
                        .header(TOKEN_HEADER, "stale-or-forged-token"))
                .andExpect(jsonPath("$.code").value(401));
    }

    /** 点赞快捷方法。 */
    private void like(String token, long cid) throws Exception {
        mockMvc.perform(post("/api/comment/{cid}/like", cid)
                        .header(TOKEN_HEADER, token))
                .andExpect(status().isOk());
    }

    /**
     * 在某歌主评论列表里找到指定 cid 的记录节点（带当前 token 身份，便于读 likedByMe）。
     *
     * @param token 会话令牌（影响 likedByMe）
     * @param sid   歌曲 sid
     * @param cid   目标主评论 cid
     * @return 该评论的 JSON 节点
     */
    private JsonNode findRecord(String token, long sid, long cid) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/comment/song/{sid}", sid)
                        .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode rec : readJson(res).path("data").path("records")) {
            if (rec.path("cid").asLong() == cid) {
                return rec;
            }
        }
        throw new AssertionError("未在 sid=" + sid + " 主评论列表中找到 cid=" + cid);
    }

    /** 读取某主评论当前点赞数。 */
    private long likeCountOf(String token, long sid, long cid) throws Exception {
        return findRecord(token, sid, cid).path("likeCount").asLong();
    }
}
