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
 * 用户与鉴权模块 API 集成测试。
 *
 * <p>覆盖 {@code com.music.user} 的注册/登录/登出与个人中心接口，
 * 以及会话拦截器的鉴权行为（401/403）。这是其余模块鉴权的地基用例。</p>
 *
 * <p>基于 {@code 03_seed.sql} 真实种子：admin(uid=1,role=2)、uploader_jay(uid=2,role=1)、
 * alice(uid=5,role=0)、banned_user(uid=9,status=1 封禁)、ghost(uid=10,is_deleted 软删)，
 * 明文密码均 123456。基类 {@code @Transactional} 保证每用例回滚，注册/改资料不污染种子。</p>
 *
 * <p>错误码口径：单参 {@code BizException} → BIZ_ERROR=409（如"用户名已存在"/"旧密码不正确"）；
 * {@code @Valid} 校验失败 → 400；未登录/失效 → 401；封禁/软删登录 → 403。</p>
 */
class UserApiTest extends AbstractIntegrationTest {

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

    // ============================ 注册 ============================

    @Test
    @DisplayName("注册新用户：成功返回 uid，默认普通角色")
    void register() throws Exception {
        String body = """
                {"username":"newcomer","password":"123456","nickname":"新人","email":"new@mail.com"}
                """;
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        assertThat(readJson(res).path("data").asLong()).isPositive();

        // 注册后可直接登录，且角色为普通用户(0)
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"newcomer","password":"123456"}
                                """))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        assertThat(readJson(login).path("data").path("role").asInt()).isEqualTo(0);
    }

    @Test
    @DisplayName("注册用户名重复：409")
    void registerDuplicateUsername() throws Exception {
        // alice 是种子已存在用户名
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"123456","nickname":"冒名者"}
                                """))
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("注册邮箱重复：409")
    void registerDuplicateEmail() throws Exception {
        // alice@mail.com 已被占用
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"brandnew","password":"123456","nickname":"新人","email":"alice@mail.com"}
                                """))
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("注册用户名过短(<4)：400 参数校验")
    void registerUsernameTooShort() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ab","password":"123456","nickname":"短名"}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("注册密码过短(<6)：400 参数校验")
    void registerPasswordTooShort() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"validname","password":"123","nickname":"弱密码"}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("注册邮箱格式非法：400 参数校验")
    void registerInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"validname","password":"123456","nickname":"昵称","email":"not-an-email"}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ============================ 登录 ============================

    @Test
    @DisplayName("正确账号密码登录：成功返回 token 与用户信息")
    void loginSuccess() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = readJson(res).path("data");
        assertThat(data.path("token").asText()).isNotBlank();
        assertThat(data.path("uid").asLong()).isEqualTo(5);
        assertThat(data.path("username").asText()).isEqualTo("alice");
        assertThat(data.path("role").asInt()).isEqualTo(0);
    }

    @Test
    @DisplayName("密码错误：401")
    void loginWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"wrongpass"}
                                """))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("用户名不存在：401（与密码错误同提示，不暴露用户名是否存在）")
    void loginUnknownUser() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nobody_here","password":"123456"}
                                """))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("封禁账号登录：403")
    void loginBannedUser() throws Exception {
        // banned_user(uid=9) status=1 封禁
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"banned_user","password":"123456"}
                                """))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("已软删账号登录：403")
    void loginDeletedUser() throws Exception {
        // ghost(uid=10) is_deleted=true
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ghost","password":"123456"}
                                """))
                .andExpect(jsonPath("$.code").value(403));
    }

    // ============================ 个人中心 ============================

    @Test
    @DisplayName("查询当前用户资料：返回安全字段，不含密码")
    void getMe() throws Exception {
        String token = login("alice", "123456");
        MvcResult res = mockMvc.perform(get("/api/user/me").header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = readJson(res).path("data");
        assertThat(data.path("uid").asLong()).isEqualTo(5);
        assertThat(data.path("username").asText()).isEqualTo("alice");
        assertThat(data.path("email").asText()).isEqualTo("alice@mail.com");
        // 安全投影：绝不返回 password 字段
        assertThat(data.has("password")).isFalse();
    }

    @Test
    @DisplayName("未登录查询资料：401")
    void getMeAnonymous() throws Exception {
        mockMvc.perform(get("/api/user/me"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("带失效/伪造令牌查询资料：401")
    void getMeWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/user/me").header(TOKEN_HEADER, "garbage-token-xxx"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("修改个人资料：昵称更新生效")
    void updateProfile() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(put("/api/user/profile")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"爱丽丝改名了","avatar":"/avatar/new.png"}
                                """))
                .andExpect(jsonPath("$.code").value(200));

        MvcResult res = mockMvc.perform(get("/api/user/me").header(TOKEN_HEADER, token))
                .andReturn();
        assertThat(readJson(res).path("data").path("nickname").asText()).isEqualTo("爱丽丝改名了");
    }

    @Test
    @DisplayName("修改资料昵称为空：400 参数校验")
    void updateProfileBlankNickname() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(put("/api/user/profile")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":""}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("修改密码：旧密码正确则更新，新密码可登录、旧密码失效")
    void updatePassword() throws Exception {
        String token = login("bob", "123456");
        mockMvc.perform(put("/api/user/password")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"123456","newPassword":"newpass888"}
                                """))
                .andExpect(jsonPath("$.code").value(200));

        // 新密码可登录
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"bob","password":"newpass888"}
                                """))
                .andExpect(jsonPath("$.code").value(200));
        // 旧密码失效
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"bob","password":"123456"}
                                """))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("修改密码旧密码错误：409")
    void updatePasswordWrongOld() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(put("/api/user/password")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"wrongold","newPassword":"newpass888"}
                                """))
                .andExpect(jsonPath("$.code").value(409));
    }

    // ============================ 登出 ============================

    @Test
    @DisplayName("登出后令牌失效：再用同一令牌访问受保护接口返回 401")
    void logoutInvalidatesToken() throws Exception {
        String token = login("alice", "123456");
        // 登出前可正常访问
        mockMvc.perform(get("/api/user/me").header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200));
        // 登出
        mockMvc.perform(post("/api/auth/logout").header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200));
        // 登出后同一令牌失效
        mockMvc.perform(get("/api/user/me").header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("登出未携带令牌：401（拦截器拦截）")
    void logoutAnonymous() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================ 校验边界补充 ============================

    @Test
    @DisplayName("登录用户名为空：400（@NotBlank，区别于密码错误的 401）")
    void loginBlankUsername() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":"123456"}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("注册用户名超长(>50)：400")
    void registerUsernameTooLong() throws Exception {
        String longName = "a".repeat(51);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + longName + "\",\"password\":\"123456\",\"nickname\":\"长名\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("注册密码超长(>50)：400")
    void registerPasswordTooLong() throws Exception {
        String longPwd = "a".repeat(51);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"validname\",\"password\":\"" + longPwd + "\",\"nickname\":\"昵称\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("注册昵称为空：400（@NotBlank）")
    void registerBlankNickname() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"validname","password":"123456","nickname":""}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("注册昵称超长(>50)：400")
    void registerNicknameTooLong() throws Exception {
        String longNick = "昵".repeat(51);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"validname\",\"password\":\"123456\",\"nickname\":\"" + longNick + "\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("注册用户名下限端点(恰好 4 字符)：成功（合法端点不被误拒）")
    void registerUsernameMinBoundary() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"abcd","password":"123456","nickname":"四字名"}
                                """))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        assertThat(readJson(res).path("data").asLong()).isPositive();
    }

    @Test
    @DisplayName("改密新密码过短(<6)：400（区别于旧密码错误的 409）")
    void updatePasswordNewTooShort() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(put("/api/user/password")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"123456","newPassword":"123"}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("改密旧密码为空：400（@NotBlank，区别于旧密码错误的 409）")
    void updatePasswordBlankOld() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(put("/api/user/password")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"","newPassword":"newpass888"}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("改资料头像超长(>255)：400")
    void updateProfileAvatarTooLong() throws Exception {
        String token = login("alice", "123456");
        String longAvatar = "/a/".repeat(100); // 300 字符 > 255
        mockMvc.perform(put("/api/user/profile")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"爱丽丝\",\"avatar\":\"" + longAvatar + "\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }
}
