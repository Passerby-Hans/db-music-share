package com.music.song;

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

import java.net.HttpURLConnection;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 流式播放（"边放边加载"）集成测试 —— 方案 a：客户端直连预签名 URL。
 *
 * <p>本系统不做后端流式代理，而是 {@code GET /api/song/public/{sid}/play-url} 返回
 * MinIO 限时预签名 URL，前端 {@code <audio>} 直接用它播放。"边放边加载"依赖
 * <b>HTTP Range 请求</b>：浏览器对 audio 源自动发 {@code Range} 头分段拉取、拖动进度只取所需片段，
 * 而 MinIO 对预签名 GET 原生支持 Range（返回 206 Partial Content）。</p>
 *
 * <p>本测试固化这条链路：上传真实音频 {@code src/test/resources/sample-audio.wav}（约 21MB WAV）
 * → 建歌 → 管理员审核通过 → 取 play-url → 对该 URL 发 Range 请求验证 206 与分段字节，
 * 并验证整段完整性。这是后端零改动即支持流式播放的证据。</p>
 *
 * <p>账号取自 {@code 03_seed.sql}：uploader_jay(uid=2,role=1 上传者)、admin(uid=1,role=2)，
 * 明文密码均 123456。每个用例由基类 {@code @Transactional} 回滚 DB（不污染种子）。</p>
 */
class StreamPlaybackApiTest extends AbstractIntegrationTest {

    /** 会话令牌请求头名（与 AuthInterceptor.TOKEN_HEADER 一致）。 */
    private static final String TOKEN_HEADER = "X-Token";

    /** 测试音频资源（真实 WAV）。 */
    private static final String SAMPLE_AUDIO = "sample-audio.wav";

    /** 该资源的总字节数（用于完整性断言）。 */
    private static final long SAMPLE_AUDIO_SIZE = 21527454L;

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

    /**
     * 端到端建一首"可播放"的歌：uploader 上传真实音频 → 建歌(自动缺省专辑) → admin 审核通过。
     *
     * @return 已通过审核、口径A可见的歌曲 sid
     */
    private long createPlayableSong() throws Exception {
        // 1. 上传者登录并上传真实音频，拿 object key
        String uploader = login("uploader_jay", "123456");
        byte[] audioBytes = new ClassPathResource(SAMPLE_AUDIO).getInputStream().readAllBytes();
        MockMultipartFile audio =
                new MockMultipartFile("file", SAMPLE_AUDIO, "audio/wav", audioBytes);
        MvcResult up = mockMvc.perform(multipart("/api/file/audio")
                        .file(audio).header(TOKEN_HEADER, uploader))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        String audioKey = readJson(up).path("data").path("key").asText();
        assertThat(audioKey).isNotBlank();

        // 2. 建歌（不指定专辑 → 自动生成缺省专辑），待审
        String body = """
                {"title":"流式测试曲","audioPath":"%s","duration":122}
                """.formatted(audioKey);
        MvcResult created = mockMvc.perform(post("/api/song")
                        .header(TOKEN_HEADER, uploader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        long sid = readJson(created).path("data").asLong();

        // 3. 管理员审核通过 → 进入口径A（可播放）
        String admin = login("admin", "123456");
        mockMvc.perform(post("/api/admin/song/{sid}/audit", sid)
                        .header(TOKEN_HEADER, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pass":true}
                                """))
                .andExpect(jsonPath("$.code").value(200));
        return sid;
    }

    /** 取某歌的预签名播放 URL。 */
    private String fetchPlayUrl(String token, long sid) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/song/public/{sid}/play-url", sid)
                        .header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return readJson(res).path("data").asText();
    }

    // ============================ 链路 / 可见性 ============================

    @Test
    @DisplayName("可播放歌曲返回非空预签名 URL，指向音频私有桶")
    void playUrlReturned() throws Exception {
        long sid = createPlayableSong();
        String url = fetchPlayUrl(login("uploader_jay", "123456"), sid);
        // 预签名 URL 含签名查询参数（MinIO/S3 风格），非空且带 query
        assertThat(url).isNotBlank().contains("?");
    }

    @Test
    @DisplayName("待审歌曲取播放地址：404（口径A不可见，不发预签名 URL）")
    void pendingSongNoPlayUrl() throws Exception {
        // 上传+建歌但不审核，停留待审
        String uploader = login("uploader_jay", "123456");
        byte[] audioBytes = new ClassPathResource(SAMPLE_AUDIO).getInputStream().readAllBytes();
        MvcResult up = mockMvc.perform(multipart("/api/file/audio")
                        .file(new MockMultipartFile("file", SAMPLE_AUDIO, "audio/wav", audioBytes))
                        .header(TOKEN_HEADER, uploader))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        String audioKey = readJson(up).path("data").path("key").asText();
        MvcResult created = mockMvc.perform(post("/api/song")
                        .header(TOKEN_HEADER, uploader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"未审流式曲","audioPath":"%s"}
                                """.formatted(audioKey)))
                .andReturn();
        long sid = readJson(created).path("data").asLong();
        // 待审 → 取播放地址 404
        mockMvc.perform(get("/api/song/public/{sid}/play-url", sid)
                        .header(TOKEN_HEADER, uploader))
                .andExpect(jsonPath("$.code").value(404));
    }

    // ============================ 流式 / Range（边放边加载核心） ============================

    @Test
    @DisplayName("Range 请求前 1024 字节：返回 206 + Content-Range，正好 1024 字节")
    void rangeRequestReturnsPartialContent() throws Exception {
        long sid = createPlayableSong();
        String url = fetchPlayUrl(login("uploader_jay", "123456"), sid);

        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Range", "bytes=0-1023");
        int code = conn.getResponseCode();
        // 206 Partial Content —— "边放边加载"的协议基础
        assertThat(code).isEqualTo(206);
        // Content-Range 形如 bytes 0-1023/21527454
        String contentRange = conn.getHeaderField("Content-Range");
        assertThat(contentRange).isNotNull()
                .startsWith("bytes 0-1023/")
                .endsWith("/" + SAMPLE_AUDIO_SIZE);
        byte[] chunk;
        try (var in = conn.getInputStream()) {
            chunk = in.readAllBytes();
        }
        // 只取回请求的那 1024 字节，而非整段
        assertThat(chunk).hasSize(1024);
    }

    @Test
    @DisplayName("Range 取中间区段：返回该区段且字节数与请求范围一致")
    void rangeRequestMiddleSegment() throws Exception {
        long sid = createPlayableSong();
        String url = fetchPlayUrl(login("uploader_jay", "123456"), sid);

        // 请求第 1,000,000 ~ 1,001,999 字节，共 2000 字节
        long start = 1_000_000L, end = 1_001_999L;
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Range", "bytes=" + start + "-" + end);
        assertThat(conn.getResponseCode()).isEqualTo(206);
        assertThat(conn.getHeaderField("Content-Range"))
                .isEqualTo("bytes " + start + "-" + end + "/" + SAMPLE_AUDIO_SIZE);
        byte[] chunk;
        try (var in = conn.getInputStream()) {
            chunk = in.readAllBytes();
        }
        assertThat(chunk).hasSize((int) (end - start + 1));
    }

    @Test
    @DisplayName("无 Range 完整 GET：200 且取回完整音频字节，与原文件一致大小")
    void fullGetReturnsWholeFile() throws Exception {
        long sid = createPlayableSong();
        String url = fetchPlayUrl(login("uploader_jay", "123456"), sid);

        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        long len;
        try (var in = conn.getInputStream()) {
            len = in.readAllBytes().length;
        }
        // 完整取回，字节数等于上传的真实文件大小（端到端完整性）
        assertThat(len).isEqualTo(SAMPLE_AUDIO_SIZE);
    }
}
