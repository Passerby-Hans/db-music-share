package com.music.song;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 歌曲/专辑模块 API 集成测试（{@code /api/song} 与 {@code /api/album}）。
 *
 * <p>覆盖上传三模式、双口径查询（公开 A / 我的 B）、改元信息回退待审、
 * 移动歌曲+空缺省专辑清理、级联软删、归属校验与角色门槛。</p>
 *
 * <p>种子（03_seed.sql）：uploader_jay(uid=2,role=1) 拥有专辑 1/2/6 与歌 1/2/3/7/11；
 * uploader_eason(uid=3,role=1) 拥有专辑 3/7 与歌 4/8/12；admin(role=2)；alice(role=0)。
 * 歌 sid=2 已通过、sid=11 驳回、sid=12 软删、sid=9/10 待审。密码均 123456。</p>
 *
 * <p>错误码：单参 BizException→409（如"歌曲已在该专辑中"/"缺省专辑不可改"）；
 * 显式 BAD_REQUEST→400；归属/角色→403；不存在/不可见→404。</p>
 */
class SongAlbumApiTest extends AbstractIntegrationTest {

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

    /** 上传歌曲，返回新 sid（body 由调用方给出，便于覆盖三模式）。 */
    private long upload(String token, String body) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/song")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return readJson(res).path("data").asLong();
    }

    /** 取某歌当前 albumAid（经"我的上传"列表，能看到任意审核态的本人歌）。 */
    private long albumOf(String token, long sid) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/song/mine")
                        .header(TOKEN_HEADER, token).param("size", "100"))
                .andReturn();
        for (JsonNode s : readJson(res).path("data").path("records")) {
            if (s.path("sid").asLong() == sid) {
                return s.path("albumAid").asLong();
            }
        }
        throw new AssertionError("我的上传里没找到 sid=" + sid);
    }

    // ============================ 上传三模式 ============================

    @Test
    @DisplayName("上传模式①：放入本人已有专辑")
    void uploadIntoOwnAlbum() throws Exception {
        String token = login("uploader_jay", "123456");
        // jay 拥有专辑 aid=1（范特西）
        long sid = upload(token, """
                {"title":"新歌入老专辑","audioPath":"audio/x.mp3","albumAid":1}
                """);
        assertThat(albumOf(token, sid)).isEqualTo(1);
    }

    @Test
    @DisplayName("上传模式②：新建专辑并放入")
    void uploadWithNewAlbum() throws Exception {
        String token = login("uploader_jay", "123456");
        long sid = upload(token, """
                {"title":"开新专辑的歌","audioPath":"audio/y.mp3","newAlbumName":"我的新专辑2026"}
                """);
        long aid = albumOf(token, sid);
        // 新建的专辑应出现在"我的专辑"且名字匹配
        MvcResult res = mockMvc.perform(get("/api/album/mine")
                        .header(TOKEN_HEADER, token).param("size", "100"))
                .andReturn();
        boolean found = false;
        for (JsonNode a : readJson(res).path("data").path("records")) {
            if (a.path("aid").asLong() == aid) {
                assertThat(a.path("albumName").asText()).isEqualTo("我的新专辑2026");
                assertThat(a.path("isDefault").asBoolean()).isFalse();
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    @DisplayName("上传模式③：不指定专辑，自动生成缺省专辑（名取歌名，isDefault=true）")
    void uploadAutoDefaultAlbum() throws Exception {
        String token = login("uploader_jay", "123456");
        long sid = upload(token, """
                {"title":"孤独的单曲","audioPath":"audio/z.mp3"}
                """);
        long aid = albumOf(token, sid);
        MvcResult res = mockMvc.perform(get("/api/album/mine")
                        .header(TOKEN_HEADER, token).param("size", "100"))
                .andReturn();
        boolean found = false;
        for (JsonNode a : readJson(res).path("data").path("records")) {
            if (a.path("aid").asLong() == aid) {
                assertThat(a.path("isDefault").asBoolean()).isTrue();
                assertThat(a.path("albumName").asText()).isEqualTo("孤独的单曲");
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    @DisplayName("上传同时指定已有专辑和新建专辑名：400（两模式互斥）")
    void uploadBothAlbumModesRejected() throws Exception {
        String token = login("uploader_jay", "123456");
        mockMvc.perform(post("/api/song")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"矛盾的歌","audioPath":"audio/c.mp3","albumAid":1,"newAlbumName":"还要新建"}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("上传到他人专辑：403")
    void uploadIntoOthersAlbum() throws Exception {
        // jay 尝试传入 eason 的专辑 aid=3
        String token = login("uploader_jay", "123456");
        mockMvc.perform(post("/api/song")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"越界上传","audioPath":"audio/d.mp3","albumAid":3}
                                """))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("上传到不存在的专辑：404")
    void uploadIntoMissingAlbum() throws Exception {
        String token = login("uploader_jay", "123456");
        mockMvc.perform(post("/api/song")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"传入幽灵专辑","audioPath":"audio/e.mp3","albumAid":99999}
                                """))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("普通用户(role=0)上传：403（需 role≥1）")
    void uploadAsNormalUserForbidden() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(post("/api/song")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"普通用户想传","audioPath":"audio/f.mp3"}
                                """))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("上传缺标题：400 参数校验")
    void uploadMissingTitle() throws Exception {
        String token = login("uploader_jay", "123456");
        mockMvc.perform(post("/api/song")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"audioPath":"audio/g.mp3"}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("未登录上传：401")
    void uploadAnonymous() throws Exception {
        mockMvc.perform(post("/api/song")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"游客上传","audioPath":"audio/h.mp3"}
                                """))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================ 公开歌曲列表（口径A） ============================

    @Test
    @DisplayName("公开歌曲列表：仅返回已审核+未删的歌曲，sid 倒序")
    void listPublicReturnsApprovedOnly() throws Exception {
        // 注意：/api/song/public 虽名为"公开"，但仍需登录（未标注 @OptionalAuth）
        String token = login("alice", "123456");
        MvcResult res = mockMvc.perform(get("/api/song/public")
                        .header(TOKEN_HEADER, token).param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andReturn();
        JsonNode records = readJson(res).path("data").path("records");
        assertThat(records).isNotEmpty();
        for (JsonNode s : records) {
            // 口径A：每条都是已审核(auditStatus=1)、未删
            assertThat(s.path("auditStatus").asInt()).isEqualTo(1);
            assertThat(s.path("isDeleted").asBoolean()).isFalse();
        }
        // 确认已通过歌在列表中（sid=1 简单爱）
        boolean hasSid1 = false;
        for (JsonNode s : records) {
            if (s.path("sid").asLong() == 1L) { hasSid1 = true; break; }
        }
        assertThat(hasSid1).isTrue();
    }

    @Test
    @DisplayName("公开歌曲列表按关键词搜索：'晴天' 只返回匹配项")
    void listPublicKeywordSearch() throws Exception {
        String token = login("alice", "123456");
        MvcResult res = mockMvc.perform(get("/api/song/public")
                        .header(TOKEN_HEADER, token)
                        .param("keyword", "晴天")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode records = readJson(res).path("data").path("records");
        assertThat(records).isNotEmpty();
        for (JsonNode s : records) {
            assertThat(s.path("title").asText()).contains("晴天");
        }
    }

    @Test
    @DisplayName("公开歌曲列表排除待审/驳回/软删歌曲")
    void listPublicExcludesNonApproved() throws Exception {
        String token = login("alice", "123456");
        MvcResult res = mockMvc.perform(get("/api/song/public")
                        .header(TOKEN_HEADER, token).param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = readJson(res).path("data").path("records");
        Set<Long> sids = new java.util.HashSet<>();
        for (JsonNode s : records) {
            sids.add(s.path("sid").asLong());
        }
        // sid=9(待审)、sid=10(待审)、sid=11(驳回)、sid=12(软删) 不应在其中
        assertThat(sids).doesNotContain(9L, 10L, 11L, 12L);
    }

    // ============================ 公开歌曲详情（口径A） ============================

    @Test
    @DisplayName("公开详情：已通过歌曲返回完整信息")
    void getPublicDetailApproved() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(get("/api/song/public/{sid}", 1).header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sid").value(1))
                .andExpect(jsonPath("$.data.title").value("简单爱"))
                .andExpect(jsonPath("$.data.auditStatus").value(1));
    }

    @Test
    @DisplayName("公开详情：待审歌不可见→404")
    void getPublicDetailPendingNotFound() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(get("/api/song/public/{sid}", 9).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("公开详情：已驳回歌不可见→404")
    void getPublicDetailRejectedNotFound() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(get("/api/song/public/{sid}", 11).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("公开详情：软删歌不可见→404")
    void getPublicDetailDeletedNotFound() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(get("/api/song/public/{sid}", 12).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("公开详情：不存在的歌→404")
    void getPublicDetailMissingNotFound() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(get("/api/song/public/{sid}", 99999).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    // ============================ 播放地址 ============================

    @Test
    @DisplayName("播放地址：已通过歌返回预签名 URL（非空字符串）")
    void getPlayUrlApproved() throws Exception {
        String token = login("alice", "123456");
        MvcResult res = mockMvc.perform(get("/api/song/public/{sid}/play-url", 1)
                        .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        String url = readJson(res).path("data").asText();
        assertThat(url).isNotBlank();
    }

    @Test
    @DisplayName("播放地址：待审歌→404")
    void getPlayUrlPendingNotFound() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(get("/api/song/public/{sid}/play-url", 9).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("播放地址：软删歌→404")
    void getPlayUrlDeletedNotFound() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(get("/api/song/public/{sid}/play-url", 12).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    // ============================ 我的上传（口径B） ============================

    @Test
    @DisplayName("我的上传：返回本人未删歌曲（含待审/驳回），sid 倒序")
    void listMineOwnSongs() throws Exception {
        // jay(uid=2) 拥有 sid=1/2/3/7(已通过) + sid=11(驳回)，均为未删
        String token = login("uploader_jay", "123456");
        MvcResult res = mockMvc.perform(get("/api/song/mine")
                        .header(TOKEN_HEADER, token)
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode records = readJson(res).path("data").path("records");
        assertThat(records).isNotEmpty();
        Set<Long> sids = new java.util.HashSet<>();
        for (JsonNode s : records) {
            assertThat(s.path("isDeleted").asBoolean()).isFalse();
            sids.add(s.path("sid").asLong());
        }
        // jay 的歌：1,2,3,7,11
        assertThat(sids).contains(1L, 2L, 3L, 7L, 11L);
        // 不含 eason 的歌
        assertThat(sids).doesNotContain(4L, 8L, 12L);
    }

    @Test
    @DisplayName("我的上传：未登录→401")
    void listMineRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/song/mine"))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================ 修改歌曲 ============================

    @Test
    @DisplayName("上传者修改本人歌曲：标题更新生效，审核态回退为待审(0)")
    void updateOwnSongResetsToPending() throws Exception {
        // jay 修改 sid=1（已通过），改后应回退到待审
        String token = login("uploader_jay", "123456");
        mockMvc.perform(put("/api/song/{sid}", 1)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"简单爱(修改版)","cover":"/cover/s1v2.jpg","duration":270}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证"我的上传"里该歌标题已变且审核态回退
        MvcResult res = mockMvc.perform(get("/api/song/mine")
                        .header(TOKEN_HEADER, token).param("size", "20"))
                .andReturn();
        JsonNode song = null;
        for (JsonNode s : readJson(res).path("data").path("records")) {
            if (s.path("sid").asLong() == 1L) { song = s; break; }
        }
        assertThat(song).isNotNull();
        assertThat(song.path("title").asText()).isEqualTo("简单爱(修改版)");
        assertThat(song.path("auditStatus").asInt()).isEqualTo(0);
    }

    @Test
    @DisplayName("管理员修改歌曲：审核态不重置（仍为已通过）")
    void adminUpdateKeepsAuditStatus() throws Exception {
        // admin 改 sid=1，审核态保持 1
        String adminToken = login("admin", "123456");
        mockMvc.perform(put("/api/song/{sid}", 1)
                        .header(TOKEN_HEADER, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"简单爱(管理员版)","duration":280}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 公开列表仍可见（auditStatus=1 未变）
        mockMvc.perform(get("/api/song/public/{sid}", 1).header(TOKEN_HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("简单爱(管理员版)"));
    }

    @Test
    @DisplayName("修改他人歌曲：403")
    void updateOthersSongForbidden() throws Exception {
        // eason 尝试改 jay 的 sid=1
        String token = login("uploader_eason", "123456");
        mockMvc.perform(put("/api/song/{sid}", 1)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"越权修改"}
                                """))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("修改软删歌曲：404")
    void updateDeletedSongNotFound() throws Exception {
        String token = login("uploader_eason", "123456");
        mockMvc.perform(put("/api/song/{sid}", 12)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"想改已下架的歌"}
                                """))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("修改歌曲标题为空：400 参数校验")
    void updateMissingTitleBadRequest() throws Exception {
        String token = login("uploader_jay", "123456");
        mockMvc.perform(put("/api/song/{sid}", 1)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","duration":270}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("未登录修改歌曲：401")
    void updateNotLoginUnauthorized() throws Exception {
        mockMvc.perform(put("/api/song/{sid}", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"游客改歌"}
                                """))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================ 移动歌曲 ============================

    @Test
    @DisplayName("移动歌曲到本人另一专辑：成功")
    void moveToOwnAlbum() throws Exception {
        // jay 把 sid=1 从专辑1(范特西) 移到专辑2(叶惠美)
        String token = login("uploader_jay", "123456");
        mockMvc.perform(put("/api/song/{sid}/album", 1)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetAlbumAid":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertThat(albumOf(token, 1)).isEqualTo(2);
    }

    @Test
    @DisplayName("移动到同一专辑：409（歌曲已在该专辑中）")
    void moveToSameAlbumConflict() throws Exception {
        // sid=1 已在专辑1，再移到专辑1
        String token = login("uploader_jay", "123456");
        mockMvc.perform(put("/api/song/{sid}/album", 1)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetAlbumAid":1}
                                """))
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("移动到他人专辑：403")
    void moveToOthersAlbumForbidden() throws Exception {
        // jay 尝试把 sid=1 移到 eason 的专辑3
        String token = login("uploader_jay", "123456");
        mockMvc.perform(put("/api/song/{sid}/album", 1)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetAlbumAid":3}
                                """))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("移动到不存在的专辑：404")
    void moveToMissingAlbumNotFound() throws Exception {
        String token = login("uploader_jay", "123456");
        mockMvc.perform(put("/api/song/{sid}/album", 1)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetAlbumAid":99999}
                                """))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("移动他人歌曲：403")
    void moveOthersSongForbidden() throws Exception {
        // eason 尝试移动 jay 的 sid=1
        String token = login("uploader_eason", "123456");
        mockMvc.perform(put("/api/song/{sid}/album", 1)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetAlbumAid":3}
                                """))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("管理员移动他人歌曲到本人专辑：成功（越过归属校验）")
    void adminMoveOthersSong() throws Exception {
        // admin 把 eason 的 sid=4 移到 jay 的专辑1
        String adminToken = login("admin", "123456");
        mockMvc.perform(put("/api/song/{sid}/album", 4)
                        .header(TOKEN_HEADER, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetAlbumAid":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("移走后源缺省专辑为空则自动软删：sid=7 移出专辑6后，专辑6消失")
    void moveTriggersDefaultAlbumCleanup() throws Exception {
        // sid=7(告白气球) 在缺省专辑 aid=6(【单曲】告白气球)，是唯一歌
        // 移走后专辑6应被清理
        String token = login("uploader_jay", "123456");
        // 确认专辑6目前在"我的专辑"中
        MvcResult before = mockMvc.perform(get("/api/album/mine")
                        .header(TOKEN_HEADER, token).param("size", "100"))
                .andReturn();
        boolean hasAid6Before = false;
        for (JsonNode a : readJson(before).path("data").path("records")) {
            if (a.path("aid").asLong() == 6L) { hasAid6Before = true; break; }
        }
        assertThat(hasAid6Before).isTrue();

        // 把 sid=7 移到专辑1
        mockMvc.perform(put("/api/song/{sid}/album", 7)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetAlbumAid":1}
                                """))
                .andExpect(status().isOk());

        // 专辑6 应已从"我的专辑"消失
        MvcResult after = mockMvc.perform(get("/api/album/mine")
                        .header(TOKEN_HEADER, token).param("size", "100"))
                .andReturn();
        boolean hasAid6After = false;
        for (JsonNode a : readJson(after).path("data").path("records")) {
            if (a.path("aid").asLong() == 6L) { hasAid6After = true; break; }
        }
        assertThat(hasAid6After).isFalse();
    }

    // ============================ 软删除歌曲 ============================

    @Test
    @DisplayName("删除本人歌曲：成功，公开列表/详情均不可见")
    void deleteOwnSong() throws Exception {
        String token = login("uploader_jay", "123456");
        mockMvc.perform(delete("/api/song/{sid}", 1).header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        // 公开详情 404
        mockMvc.perform(get("/api/song/public/{sid}", 1).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("删除他人歌曲：403")
    void deleteOthersSongForbidden() throws Exception {
        // eason 尝试删 jay 的 sid=1
        String token = login("uploader_eason", "123456");
        mockMvc.perform(delete("/api/song/{sid}", 1).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("管理员删除任意歌曲：成功")
    void adminDeleteAnySong() throws Exception {
        String adminToken = login("admin", "123456");
        mockMvc.perform(delete("/api/song/{sid}", 1).header(TOKEN_HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/song/public/{sid}", 1).header(TOKEN_HEADER, adminToken))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("删除已软删歌曲：404")
    void deleteAlreadyDeletedNotFound() throws Exception {
        // sid=12 已软删，eason 再删一次
        String token = login("uploader_eason", "123456");
        mockMvc.perform(delete("/api/song/{sid}", 12).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("删除歌曲后源缺省专辑自动清理：sid=7 删后专辑6消失")
    void deleteTriggersDefaultAlbumCleanup() throws Exception {
        // sid=7 在缺省专辑 aid=6，是唯一歌；删掉 sid=7 后 aid=6 自动清理
        String token = login("uploader_jay", "123456");

        // 确认专辑6存在
        MvcResult before = mockMvc.perform(get("/api/album/mine")
                        .header(TOKEN_HEADER, token).param("size", "100"))
                .andReturn();
        boolean hasAid6 = false;
        for (JsonNode a : readJson(before).path("data").path("records")) {
            if (a.path("aid").asLong() == 6L) { hasAid6 = true; break; }
        }
        assertThat(hasAid6).isTrue();

        // 删歌
        mockMvc.perform(delete("/api/song/{sid}", 7).header(TOKEN_HEADER, token))
                .andExpect(status().isOk());

        // 专辑6 应消失
        MvcResult after = mockMvc.perform(get("/api/album/mine")
                        .header(TOKEN_HEADER, token).param("size", "100"))
                .andReturn();
        boolean stillHas = false;
        for (JsonNode a : readJson(after).path("data").path("records")) {
            if (a.path("aid").asLong() == 6L) { stillHas = true; break; }
        }
        assertThat(stillHas).isFalse();
    }

    // ============================ 专辑新建 ============================

    @Test
    @DisplayName("上传者新建普通专辑：成功返回 aid")
    void createAlbum() throws Exception {
        String token = login("uploader_jay", "123456");
        MvcResult res = mockMvc.perform(post("/api/album")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"albumName":"测试新专辑","introduction":"集成测试用"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        long aid = readJson(res).path("data").asLong();
        assertThat(aid).isPositive();
    }

    @Test
    @DisplayName("新建专辑名称为空：400 参数校验")
    void createAlbumBlankName() throws Exception {
        String token = login("uploader_jay", "123456");
        mockMvc.perform(post("/api/album")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"albumName":""}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("普通用户新建专辑：403（需 role≥1）")
    void createAlbumAsNormalUserForbidden() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(post("/api/album")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"albumName":"alice的专辑"}
                                """))
                .andExpect(jsonPath("$.code").value(403));
    }

    // ============================ 专辑公开查询 ============================

    @Test
    @DisplayName("公开专辑列表：返回未删专辑")
    void listPublicAlbums() throws Exception {
        String token = login("alice", "123456");
        MvcResult res = mockMvc.perform(get("/api/album/public")
                        .header(TOKEN_HEADER, token).param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode records = readJson(res).path("data").path("records");
        assertThat(records).isNotEmpty();
        // 种子有 8 个专辑，均未删，全部应出现
        assertThat(readJson(res).path("data").path("total").asLong()).isGreaterThanOrEqualTo(8);
    }

    @Test
    @DisplayName("公开专辑列表按关键词搜索")
    void listPublicAlbumsWithKeyword() throws Exception {
        String token = login("alice", "123456");
        MvcResult res = mockMvc.perform(get("/api/album/public")
                        .header(TOKEN_HEADER, token)
                        .param("keyword", "范特西"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode records = readJson(res).path("data").path("records");
        assertThat(records).hasSize(1);
        assertThat(records.get(0).path("albumName").asText()).isEqualTo("范特西");
    }

    @Test
    @DisplayName("公开专辑详情：返回专辑信息+口径A可见曲目")
    void getPublicAlbumDetail() throws Exception {
        // 专辑1(范特西) 有 sid=1(通过)、sid=11(驳回)
        // 口径A只返回已通过：sid=1
        String token = login("alice", "123456");
        mockMvc.perform(get("/api/album/public/{aid}", 1).header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.album.aid").value(1))
                .andExpect(jsonPath("$.data.album.albumName").value("范特西"))
                .andExpect(jsonPath("$.data.songs").isArray());
    }

    @Test
    @DisplayName("公开专辑详情：不存在→404")
    void getPublicAlbumNotFound() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(get("/api/album/public/{aid}", 99999).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    // ============================ 我的专辑 ============================

    @Test
    @DisplayName("我的专辑列表：返回本人创建的未删专辑")
    void listMineAlbums() throws Exception {
        // jay 拥有专辑 1(范特西)/2(叶惠美)/6(缺省-告白气球)
        String token = login("uploader_jay", "123456");
        MvcResult res = mockMvc.perform(get("/api/album/mine")
                        .header(TOKEN_HEADER, token).param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        Set<Long> aids = new java.util.HashSet<>();
        for (JsonNode a : readJson(res).path("data").path("records")) {
            aids.add(a.path("aid").asLong());
        }
        assertThat(aids).contains(1L, 2L, 6L);
        assertThat(aids).doesNotContain(3L, 4L, 5L);
    }

    @Test
    @DisplayName("我的专辑列表：未登录→401")
    void listMineAlbumsRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/album/mine"))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================ 专辑修改 ============================

    @Test
    @DisplayName("修改本人普通专辑：成功")
    void updateOwnAlbum() throws Exception {
        String token = login("uploader_jay", "123456");
        mockMvc.perform(put("/api/album/{aid}", 1)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"albumName":"范特西(改版)","introduction":"更新后的介绍"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证更新生效（公开详情也需登录）
        MvcResult res = mockMvc.perform(get("/api/album/public/{aid}", 1)
                        .header(TOKEN_HEADER, token))
                .andReturn();
        assertThat(readJson(res).path("data").path("album").path("albumName").asText())
                .isEqualTo("范特西(改版)");
    }

    @Test
    @DisplayName("修改缺省专辑：409（系统托管，不可改）")
    void updateDefaultAlbumRejected() throws Exception {
        // 专辑 aid=6 是缺省专辑
        String token = login("uploader_jay", "123456");
        mockMvc.perform(put("/api/album/{aid}", 6)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"albumName":"想改缺省专辑"}
                                """))
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("修改他人专辑：403")
    void updateOthersAlbumForbidden() throws Exception {
        // eason 尝试改 jay 的专辑1
        String token = login("uploader_eason", "123456");
        mockMvc.perform(put("/api/album/{aid}", 1)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"albumName":"越权修改"}
                                """))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("修改专辑名称为空：400 参数校验")
    void updateAlbumBlankNameBadRequest() throws Exception {
        String token = login("uploader_jay", "123456");
        mockMvc.perform(put("/api/album/{aid}", 1)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"albumName":""}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ============================ 专辑删除（级联软删） ============================

    @Test
    @DisplayName("删除本人专辑：级联软删专辑内歌曲，公开列表消失")
    void deleteOwnAlbumCascade() throws Exception {
        // jay 删专辑1(范特西)，含 sid=1(通过)+sid=11(驳回)
        String token = login("uploader_jay", "123456");
        mockMvc.perform(delete("/api/album/{aid}", 1).header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        // 专辑公开详情 404
        mockMvc.perform(get("/api/album/public/{aid}", 1).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
        // 专辑内歌曲公开详情也 404
        mockMvc.perform(get("/api/song/public/{sid}", 1).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("删除他人专辑：403")
    void deleteOthersAlbumForbidden() throws Exception {
        // eason 尝试删 jay 的专辑1
        String token = login("uploader_eason", "123456");
        mockMvc.perform(delete("/api/album/{aid}", 1).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("管理员删除任意专辑：成功")
    void adminDeleteAnyAlbum() throws Exception {
        String adminToken = login("admin", "123456");
        mockMvc.perform(delete("/api/album/{aid}", 3).header(TOKEN_HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/album/public/{aid}", 3).header(TOKEN_HEADER, adminToken))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("删除不存在的专辑：404")
    void deleteMissingAlbumNotFound() throws Exception {
        String token = login("uploader_jay", "123456");
        mockMvc.perform(delete("/api/album/{aid}", 99999).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    // ============================ 校验与分支补充 ============================

    @Test
    @DisplayName("上传缺 audioPath：400（@NotBlank）")
    void uploadMissingAudioPath() throws Exception {
        String token = login("uploader_jay", "123456");
        mockMvc.perform(post("/api/song")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"没有音频路径的歌"}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("上传标题超长(>150)：400")
    void uploadTitleTooLong() throws Exception {
        String token = login("uploader_jay", "123456");
        String longTitle = "歌".repeat(151);
        mockMvc.perform(post("/api/song")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + longTitle + "\",\"audioPath\":\"audio/x.mp3\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("上传音频路径超长(>255)：400")
    void uploadAudioPathTooLong() throws Exception {
        String token = login("uploader_jay", "123456");
        String longPath = "a".repeat(256);
        mockMvc.perform(post("/api/song")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"正常名\",\"audioPath\":\"" + longPath + "\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("上传时长非正(0)：400（@Positive）")
    void uploadDurationNotPositive() throws Exception {
        String token = login("uploader_jay", "123456");
        mockMvc.perform(post("/api/song")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"零时长","audioPath":"audio/x.mp3","duration":0}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("上传模式②新专辑名超长(>100)：400")
    void uploadNewAlbumNameTooLong() throws Exception {
        String token = login("uploader_jay", "123456");
        String longName = "专".repeat(101);
        mockMvc.perform(post("/api/song")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"歌\",\"audioPath\":\"audio/x.mp3\",\"newAlbumName\":\"" + longName + "\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("移动歌曲缺 targetAlbumAid：400（@NotNull）")
    void moveMissingTarget() throws Exception {
        String token = login("uploader_jay", "123456");
        mockMvc.perform(put("/api/song/{sid}/album", 1)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("修改不存在的专辑：404（与删除路径对称）")
    void updateMissingAlbumNotFound() throws Exception {
        String token = login("uploader_jay", "123456");
        mockMvc.perform(put("/api/album/{aid}", 99999)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"albumName":"改幽灵专辑"}
                                """))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("新建专辑名超长(>100)：400")
    void createAlbumNameTooLong() throws Exception {
        String token = login("uploader_jay", "123456");
        String longName = "专".repeat(101);
        mockMvc.perform(post("/api/album")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"albumName\":\"" + longName + "\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("移走普通专辑最后一首歌：源专辑不被清理（仅缺省专辑才自动清）")
    void moveOutOfNormalAlbumKeepsIt() throws Exception {
        // jay 的专辑2(叶惠美) 是普通专辑；先把它的歌都查出来再移走，验证专辑2仍在
        // 种子里专辑2 含 sid=3（叶惠美的歌）；把 sid=3 移到专辑1后，专辑2 应仍存在
        String token = login("uploader_jay", "123456");
        mockMvc.perform(put("/api/song/{sid}/album", 3)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetAlbumAid":1}
                                """))
                .andExpect(jsonPath("$.code").value(200));
        // 专辑2 仍在"我的专辑"中（普通专辑不自动软删）
        MvcResult res = mockMvc.perform(get("/api/album/mine")
                        .header(TOKEN_HEADER, token).param("size", "100"))
                .andReturn();
        boolean stillHasAlbum2 = false;
        for (JsonNode a : readJson(res).path("data").path("records")) {
            if (a.path("aid").asLong() == 2L) { stillHasAlbum2 = true; break; }
        }
        assertThat(stillHasAlbum2).isTrue();
    }
}
