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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 评论管理接口({@code /api/admin/comment})集成测试。
 *
 * <p>覆盖管理后台全站评论列表接口:鉴权(仅 role=2 管理员)、分页、内容筛选、
 * 以及 sid + songTitle 回填。基于 {@code 03_seed.sql} 真实种子数据断言:</p>
 * <ul>
 *   <li>admin/123456(role=2 管理员)、alice/123456(role=0 普通用户);</li>
 *   <li>评论 12 条,跨 sid=2/7/4/5/1/8 多首歌(主评论 + 回复)。</li>
 * </ul>
 *
 * <p>每个用例由基类 {@code @Transactional} 保证结束回滚,不污染种子数据。
 * 鉴权走真实登录流程(POST /api/auth/login 拿 token,放入 X-Token 头)。</p>
 */
class AdminCommentApiTest extends AbstractIntegrationTest {

    /** 会话令牌请求头名(与 AuthInterceptor.TOKEN_HEADER 一致)。 */
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

    /**
     * 解析响应体为 JSON 树。
     *
     * @param res MockMvc 执行结果
     * @return JSON 根节点
     */
    private JsonNode readJson(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("管理端评论列表:全站评论分页,含 sid + songTitle 回填")
    void listAllComments() throws Exception {
        String token = login("admin", "123456");
        MvcResult res = mockMvc.perform(get("/api/admin/comment")
                        .header(TOKEN_HEADER, token).param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode records = readJson(res).path("data").path("records");
        assertThat(records.isArray()).isTrue();
        // 种子 12 条评论,首项应有数据
        assertThat(records.size()).isGreaterThan(0);
        // 首项含 sid(>0) 与回填的 songTitle(非空)
        assertThat(records.get(0).path("sid").asLong()).isGreaterThan(0);
        assertThat(records.get(0).path("songTitle").asText()).isNotBlank();
    }

    @Test
    @DisplayName("管理端评论列表 keyword 筛选:每条结果含关键词片段")
    void listAllCommentsKeyword() throws Exception {
        // 先取一条评论的内容,截前两字作 keyword 片段
        String token = login("admin", "123456");
        MvcResult first = mockMvc.perform(get("/api/admin/comment")
                        .header(TOKEN_HEADER, token).param("size", "1"))
                .andReturn();
        JsonNode rec = readJson(first).path("data").path("records").get(0);
        String content = rec.path("content").asText();
        String kw = content.length() > 2 ? content.substring(0, 2) : content;

        MvcResult res = mockMvc.perform(get("/api/admin/comment")
                        .header(TOKEN_HEADER, token).param("keyword", kw).param("size", "50"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode records = readJson(res).path("data").path("records");
        assertThat(records.size()).isGreaterThan(0);
        for (JsonNode c : records) {
            assertThat(c.path("content").asText()).contains(kw);
        }
    }

    @Test
    @DisplayName("管理端评论列表:非管理员 403")
    void listAllCommentsForbidden() throws Exception {
        // alice(role=0 普通用户)访问管理接口应被拦截
        String token = login("alice", "123456");
        mockMvc.perform(get("/api/admin/comment").header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("管理端评论列表:未登录 401")
    void listAllCommentsUnauthorized() throws Exception {
        // 不带 token,拦截器以未登录处理
        mockMvc.perform(get("/api/admin/comment"))
                .andExpect(jsonPath("$.code").value(401));
    }
}
