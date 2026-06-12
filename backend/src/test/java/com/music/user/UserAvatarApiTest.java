package com.music.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户头像上传 API 集成测试。
 *
 * <p>覆盖 {@code POST /api/user/avatar}（一步式上传头像）：上传成功后头像存入
 * 真实 MinIO 公开桶（基类已起 MINIO 容器），{@code GET /api/user/me} 返回的
 * avatar 为公开直链。验证图片校验（类型/大小）、鉴权（登录即可、普通用户亦可），
 * 以及非图片/超限的 400。</p>
 *
 * <p>账号取自 {@code 03_seed.sql}：alice(uid=5,role=0 普通用户)、明文密码 123456。
 * 关键验证「普通用户可换头像」——头像接口不限角色，区别于 /api/file/cover 的 role≥1。
 * 每个用例由基类 {@code @Transactional} 回滚 DB（avatar 字段不污染种子）。</p>
 *
 * <p>「成功上传」用例使用真实图片 {@code src/test/resources/default.jpg}（约 40KB JPEG），
 * 走完整链路存入 MinIO 并校验回链；校验类用例（非图/超限/空）用合成字节即可，
 * 它们在进存储前就被拦截。</p>
 */
class UserAvatarApiTest extends AbstractIntegrationTest {

    /** 会话令牌请求头名（与 AuthInterceptor.TOKEN_HEADER 一致）。 */
    private static final String TOKEN_HEADER = "X-Token";

    /** 测试资源中的默认头像图片（真实 JPEG），用于成功上传链路。 */
    private static final String DEFAULT_IMAGE = "default.jpg";

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

    /** 构造一个指定文件名/大小的伪图片 multipart 文件（内容非真实图片，仅测校验与存储链路）。 */
    private MockMultipartFile fakeImage(String filename, int sizeBytes) {
        return new MockMultipartFile("file", filename, "image/png", new byte[sizeBytes]);
    }

    /** 从测试资源加载真实图片（default.jpg）作为 multipart 文件，指定上传文件名。 */
    private MockMultipartFile realImage(String filename) throws Exception {
        byte[] bytes = new ClassPathResource(DEFAULT_IMAGE).getInputStream().readAllBytes();
        return new MockMultipartFile("file", filename, "image/jpeg", bytes);
    }

    @Test
    @DisplayName("普通用户上传真实图片头像：成功返回直链，me 体现新头像")
    void uploadAvatarSuccess() throws Exception {
        // alice 是普通用户(role=0)，验证头像接口不限角色（区别于 /api/file/cover 的 role≥1）
        String token = login("alice", "123456");
        MvcResult res = mockMvc.perform(multipart("/api/user/avatar")
                        .file(realImage("default.jpg"))
                        .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        String url = readJson(res).path("data").path("url").asText();
        // 返回公开直链，指向封面公开桶、保留 .jpg 扩展名
        assertThat(url).isNotBlank().contains("/").endsWith(".jpg");

        // me 接口返回的 avatar 应为该直链（已经 publicUrl 包装）
        MvcResult me = mockMvc.perform(get("/api/user/me").header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        assertThat(readJson(me).path("data").path("avatar").asText()).isEqualTo(url);
    }

    @Test
    @DisplayName("上传非图片格式(.txt)：400")
    void uploadNonImage() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(multipart("/api/user/avatar")
                        .file(fakeImage("notimage.txt", 512))
                        .header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("上传超大图片(>5MB)：400")
    void uploadOversizeImage() throws Exception {
        String token = login("alice", "123456");
        // 5MB + 1 字节，越过上限
        mockMvc.perform(multipart("/api/user/avatar")
                        .file(fakeImage("big.png", 5 * 1024 * 1024 + 1))
                        .header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("上传空文件：400")
    void uploadEmptyFile() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(multipart("/api/user/avatar")
                        .file(fakeImage("empty.png", 0))
                        .header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("未登录上传头像：401")
    void uploadAnonymous() throws Exception {
        mockMvc.perform(multipart("/api/user/avatar")
                        .file(fakeImage("me.png", 1024)))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("真实图片存入 MinIO 后可经直链取回，且字节与原图一致")
    void uploadedAvatarRetrievableAndIntact() throws Exception {
        String token = login("alice", "123456");
        byte[] original = new ClassPathResource(DEFAULT_IMAGE).getInputStream().readAllBytes();
        MvcResult res = mockMvc.perform(multipart("/api/user/avatar")
                        .file(realImage("default.jpg"))
                        .header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        String url = readJson(res).path("data").path("url").asText();

        // 经公开直链把对象 GET 回来：用 java.net 直连 MinIO（url 已是浏览器可达地址）
        java.net.HttpURLConnection conn =
                (java.net.HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        byte[] fetched;
        try (var in = conn.getInputStream()) {
            fetched = in.readAllBytes();
        }
        // 端到端二进制完整：取回字节与上传原图逐字节一致
        assertThat(fetched).isEqualTo(original);
    }

    @Test
    @DisplayName("上传伪装文件(.jpg 扩展名但内容是 HTML)：400（内容层校验拦截）")
    void uploadDisguisedHtml() throws Exception {
        // 安全回归（对抗审查 #4）：仅查扩展名不够，内容校验用 ImageIO 解码，
        // 非真实图片（这里是 HTML 文本伪装成 .jpg）应被拒，杜绝公开桶托管可渲染主动内容。
        String token = login("alice", "123456");
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        MockMultipartFile fake = new MockMultipartFile("file", "evil.jpg", "image/jpeg", html);
        mockMvc.perform(multipart("/api/user/avatar")
                        .file(fake)
                        .header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("换头像两次：第二次成功（旧头像清理走提交后钩子，不影响结果）")
    void uploadAvatarTwice() throws Exception {
        String token = login("alice", "123456");
        // 第一次上传真实图
        mockMvc.perform(multipart("/api/user/avatar")
                        .file(realImage("first.jpg"))
                        .header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200));
        // 第二次上传（此时旧头像是 avatar/ 前缀的上传对象，触发删旧逻辑）
        MvcResult res = mockMvc.perform(multipart("/api/user/avatar")
                        .file(realImage("second.jpg"))
                        .header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        assertThat(readJson(res).path("data").path("url").asText()).isNotBlank();
    }
}
