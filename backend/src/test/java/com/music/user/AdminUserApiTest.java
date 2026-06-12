package com.music.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理后台 - 用户管理 API 集成测试。
 *
 * <p>覆盖 {@code com.music.user} 的管理端接口（{@code /api/admin/user}）：
 * 用户列表（分页/搜索/角色/状态筛选）、详情、封禁、解封、改角色。
 * 整组接口要求 role=2，验证非管理员 403、未登录 401 的门禁。</p>
 *
 * <p>基于 {@code 03_seed.sql} 真实种子：admin(uid=1,role=2)、uploader_jay(uid=2,role=1)、
 * alice(uid=5,role=0)、bob(uid=6,role=0)、carol(uid=7,role=0)、david(uid=8,role=0)、
 * banned_user(uid=9,status=1 封禁)、ghost(uid=10,is_deleted 软删)，明文密码均 123456。
 * 未软删用户共 9 个（ghost 不计入列表）。</p>
 *
 * <p>关键验证「即时生效」：封禁/改角色后调 {@code deleteSessionsByUid} 作废目标会话——
 * 基类 {@code @Transactional} 只回滚 DB、不回滚 Redis，故被封用户旧令牌可即时断言失效。
 * 每个用例 DB 改动结束回滚，不污染种子。</p>
 */
class AdminUserApiTest extends AbstractIntegrationTest {

    /** 会话令牌请求头名（与 AuthInterceptor.TOKEN_HEADER 一致）。 */
    private static final String TOKEN_HEADER = "X-Token";

    @Autowired
    private ObjectMapper objectMapper;

    /** 走真实登录接口换取会话令牌。 */
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

    /** 解析响应体为 JSON 树。 */
    private JsonNode readJson(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    // ============================ 列表 / 详情 ============================

    @Test
    @DisplayName("管理员查用户列表：分页返回，含未软删用户、不含 ghost")
    void listUsers() throws Exception {
        String admin = login("admin", "123456");
        MvcResult res = mockMvc.perform(get("/api/admin/user")
                        .header(TOKEN_HEADER, admin).param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andReturn();
        JsonNode data = readJson(res).path("data");
        // 未软删用户 9 个（uid 1~9），ghost(uid=10) 被排除
        assertThat(data.path("total").asLong()).isEqualTo(9);
        for (JsonNode rec : data.path("records")) {
            assertThat(rec.path("uid").asLong()).isNotEqualTo(10L);
            // 安全投影：列表项不含密码
            assertThat(rec.has("password")).isFalse();
            // 管理视图暴露 status，供识别封禁账号
            assertThat(rec.has("status")).isTrue();
        }
    }

    @Test
    @DisplayName("按关键字搜索用户：命中用户名或昵称")
    void listUsersByKeyword() throws Exception {
        String admin = login("admin", "123456");
        MvcResult res = mockMvc.perform(get("/api/admin/user")
                        .header(TOKEN_HEADER, admin).param("keyword", "alice"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode records = readJson(res).path("data").path("records");
        assertThat(records).isNotEmpty();
        for (JsonNode rec : records) {
            assertThat(rec.path("username").asText()).contains("alice");
        }
    }

    @Test
    @DisplayName("按状态筛选：status=1 仅返回封禁用户")
    void listUsersByStatus() throws Exception {
        String admin = login("admin", "123456");
        MvcResult res = mockMvc.perform(get("/api/admin/user")
                        .header(TOKEN_HEADER, admin).param("status", "1").param("size", "50"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = readJson(res).path("data");
        // 种子仅 banned_user(uid=9) 处于封禁态
        assertThat(data.path("total").asLong()).isEqualTo(1);
        assertThat(data.path("records").get(0).path("uid").asLong()).isEqualTo(9);
    }

    @Test
    @DisplayName("按角色筛选：role=1 仅返回上传者")
    void listUsersByRole() throws Exception {
        String admin = login("admin", "123456");
        MvcResult res = mockMvc.perform(get("/api/admin/user")
                        .header(TOKEN_HEADER, admin).param("role", "1").param("size", "50"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = readJson(res).path("data");
        // 种子上传者 uid=2/3/4 共 3 个
        assertThat(data.path("total").asLong()).isEqualTo(3);
        for (JsonNode rec : data.path("records")) {
            assertThat(rec.path("role").asInt()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("查用户详情：返回 status、不含密码")
    void getUserDetail() throws Exception {
        String admin = login("admin", "123456");
        MvcResult res = mockMvc.perform(get("/api/admin/user/{uid}", 5)
                        .header(TOKEN_HEADER, admin))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = readJson(res).path("data");
        assertThat(data.path("uid").asLong()).isEqualTo(5);
        assertThat(data.path("username").asText()).isEqualTo("alice");
        assertThat(data.path("status").asInt()).isEqualTo(0);
        assertThat(data.has("password")).isFalse();
    }

    @Test
    @DisplayName("查不存在用户详情：404")
    void getUserDetailNotFound() throws Exception {
        String admin = login("admin", "123456");
        mockMvc.perform(get("/api/admin/user/{uid}", 99999)
                        .header(TOKEN_HEADER, admin))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("查已软删用户(ghost uid=10)详情：404")
    void getDeletedUserDetail() throws Exception {
        String admin = login("admin", "123456");
        mockMvc.perform(get("/api/admin/user/{uid}", 10)
                        .header(TOKEN_HEADER, admin))
                .andExpect(jsonPath("$.code").value(404));
    }

    // ============================ 封禁 / 解封 ============================

    @Test
    @DisplayName("封禁用户后即时踢下线：被封者旧令牌访问受保护接口转 401，且无法再登录")
    void banKicksUserOffline() throws Exception {
        // alice 先登录拿到令牌，确认可用
        String aliceToken = login("alice", "123456");
        mockMvc.perform(get("/api/user/me").header(TOKEN_HEADER, aliceToken))
                .andExpect(jsonPath("$.code").value(200));
        // 管理员封禁 alice(uid=5)
        String admin = login("admin", "123456");
        mockMvc.perform(put("/api/admin/user/{uid}/ban", 5).header(TOKEN_HEADER, admin))
                .andExpect(jsonPath("$.code").value(200));
        // 旧令牌即时失效（会话被作废）
        mockMvc.perform(get("/api/user/me").header(TOKEN_HEADER, aliceToken))
                .andExpect(jsonPath("$.code").value(401));
        // 被封后也无法重新登录（登录接口拒绝封禁账号 403）
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"123456"}
                                """))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("封禁幂等且重试仍吊销(对抗审查#3)：对已封用户再次封禁返回 200，不 early-return")
    void banIsIdempotent() throws Exception {
        // #3 回归：旧实现「已 banned 即 return」会在『DB 已封但上次 Redis 吊销失败』时
        // 跳过清会话，使漏吊销的旧会话续命至 TTL。修复后即便已 banned 也会重复执行吊销，
        // 故对已封禁用户(uid=9)重复封禁仍应正常返回 200（而非因 early-return 改变行为）。
        String admin = login("admin", "123456");
        mockMvc.perform(put("/api/admin/user/{uid}/ban", 9).header(TOKEN_HEADER, admin))
                .andExpect(jsonPath("$.code").value(200));
        // 再封一次，依旧 200（幂等 + 每次都执行吊销）
        mockMvc.perform(put("/api/admin/user/{uid}/ban", 9).header(TOKEN_HEADER, admin))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("封禁对已登录会话即时生效后重复封禁仍成功：alice 登录→封→再封")
    void banTwiceAfterActiveSession() throws Exception {
        // alice 先登录产生活跃会话
        String aliceToken = login("alice", "123456");
        String admin = login("admin", "123456");
        // 第一次封禁：清掉 alice 会话
        mockMvc.perform(put("/api/admin/user/{uid}/ban", 5).header(TOKEN_HEADER, admin))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/user/me").header(TOKEN_HEADER, aliceToken))
                .andExpect(jsonPath("$.code").value(401));
        // 第二次封禁（alice 已是 banned）：仍走吊销路径、返回 200，不因 early-return 短路
        mockMvc.perform(put("/api/admin/user/{uid}/ban", 5).header(TOKEN_HEADER, admin))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("解封用户：被封者解封后可重新登录")
    void unbanRestoresLogin() throws Exception {
        String admin = login("admin", "123456");
        // banned_user(uid=9) 初始被封，登录应 403
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"banned_user","password":"123456"}
                                """))
                .andExpect(jsonPath("$.code").value(403));
        // 解封
        mockMvc.perform(put("/api/admin/user/{uid}/unban", 9).header(TOKEN_HEADER, admin))
                .andExpect(jsonPath("$.code").value(200));
        // 解封后可正常登录
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"banned_user","password":"123456"}
                                """))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("解封幂等：解封未封禁用户(uid=5)直接成功")
    void unbanIsIdempotent() throws Exception {
        String admin = login("admin", "123456");
        mockMvc.perform(put("/api/admin/user/{uid}/unban", 5).header(TOKEN_HEADER, admin))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("封禁不存在用户：404")
    void banNotFound() throws Exception {
        String admin = login("admin", "123456");
        mockMvc.perform(put("/api/admin/user/{uid}/ban", 99999).header(TOKEN_HEADER, admin))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("自我保护：管理员不能封禁自己（uid=1）→ 400")
    void cannotBanSelf() throws Exception {
        String admin = login("admin", "123456");
        mockMvc.perform(put("/api/admin/user/{uid}/ban", 1).header(TOKEN_HEADER, admin))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ============================ 改角色 ============================

    @Test
    @DisplayName("改角色：把普通用户 bob(uid=6) 升为上传者，详情体现新角色")
    void changeRolePromotes() throws Exception {
        String admin = login("admin", "123456");
        mockMvc.perform(put("/api/admin/user/{uid}/role", 6)
                        .header(TOKEN_HEADER, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":1}
                                """))
                .andExpect(jsonPath("$.code").value(200));
        MvcResult res = mockMvc.perform(get("/api/admin/user/{uid}", 6)
                        .header(TOKEN_HEADER, admin))
                .andReturn();
        assertThat(readJson(res).path("data").path("role").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("改角色后即时作废目标会话：被改者旧令牌转 401")
    void changeRoleRevokesTargetSessions() throws Exception {
        // bob 先登录
        String bobToken = login("bob", "123456");
        mockMvc.perform(get("/api/user/me").header(TOKEN_HEADER, bobToken))
                .andExpect(jsonPath("$.code").value(200));
        // 管理员把 bob 升为上传者
        String admin = login("admin", "123456");
        mockMvc.perform(put("/api/admin/user/{uid}/role", 6)
                        .header(TOKEN_HEADER, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":1}
                                """))
                .andExpect(jsonPath("$.code").value(200));
        // bob 旧令牌（缓存旧角色）被作废，转 401，须重新登录获取新角色
        mockMvc.perform(get("/api/user/me").header(TOKEN_HEADER, bobToken))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("改角色非法值(role=3)：400 参数校验")
    void changeRoleInvalidValue() throws Exception {
        String admin = login("admin", "123456");
        mockMvc.perform(put("/api/admin/user/{uid}/role", 6)
                        .header(TOKEN_HEADER, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":3}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("改角色缺 role 字段：400")
    void changeRoleMissingField() throws Exception {
        String admin = login("admin", "123456");
        mockMvc.perform(put("/api/admin/user/{uid}/role", 6)
                        .header(TOKEN_HEADER, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("自我保护：管理员不能改自己的角色（uid=1）→ 400")
    void cannotChangeSelfRole() throws Exception {
        String admin = login("admin", "123456");
        mockMvc.perform(put("/api/admin/user/{uid}/role", 1)
                        .header(TOKEN_HEADER, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":0}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("改不存在用户角色：404")
    void changeRoleNotFound() throws Exception {
        String admin = login("admin", "123456");
        mockMvc.perform(put("/api/admin/user/{uid}/role", 99999)
                        .header(TOKEN_HEADER, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":1}
                                """))
                .andExpect(jsonPath("$.code").value(404));
    }

    // ============================ 门禁（角色/登录） ============================

    @Test
    @DisplayName("非管理员访问管理接口：普通用户 alice 查用户列表 → 403")
    void normalUserForbidden() throws Exception {
        String alice = login("alice", "123456");
        mockMvc.perform(get("/api/admin/user").header(TOKEN_HEADER, alice))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("上传者访问管理接口：uploader_jay 封禁他人 → 403")
    void uploaderForbidden() throws Exception {
        String jay = login("uploader_jay", "123456");
        mockMvc.perform(put("/api/admin/user/{uid}/ban", 5).header(TOKEN_HEADER, jay))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("未登录访问管理接口：401")
    void anonymousUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/user"))
                .andExpect(jsonPath("$.code").value(401));
    }
}
