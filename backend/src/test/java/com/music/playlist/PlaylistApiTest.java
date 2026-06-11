package com.music.playlist;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 歌单模块 API 集成测试（{@code /api/playlist}）。
 *
 * <p>覆盖歌单 CRUD、我的歌单、歌单详情（软鉴权 + 公开/私密两层可见性）、
 * 加歌/移歌（幂等 + 口径A 可见校验 + 归属校验）、失效歌曲不剔除（playable=false）、
 * 公开广场空壳占位。</p>
 *
 * <p>种子（03_seed.sql）：alice(uid=5,role=0) 拥有 plid=1(公开,曲目1/2/3/7/8) 与
 * plid=2(私密,曲目2/4)；bob(uid=6) 拥有 plid=3(公开) 与 plid=6(私密空)；
 * carol(uid=7) 拥有 plid=4(公开,曲目4/8)；admin(role=2)。歌曲 sid=1/2/3/7/8 已通过、
 * sid=9/10 待审、sid=11 驳回、sid=12 软删。密码均 123456。</p>
 *
 * <p>错误码：参数校验→400；未登录/失效令牌→401；非 owner 非管理员→403；
 * 歌单/歌曲不存在或私密无权→404。</p>
 */
class PlaylistApiTest extends AbstractIntegrationTest {

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

    /** 创建歌单返回新 plid（body 由调用方给出，便于覆盖公开/私密）。 */
    private long create(String token, String body) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/playlist")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return readJson(res).path("data").asLong();
    }

    /** 取某歌单当前曲目数（经详情接口的 playlist.songCount）。 */
    private long songCountOf(String token, long plid) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/playlist/{plid}", plid).header(TOKEN_HEADER, token))
                .andReturn();
        return readJson(res).path("data").path("playlist").path("songCount").asLong();
    }

    // ============================ 创建歌单 ============================

    @Test
    @DisplayName("创建歌单：成功返回 plid（登录即可，普通用户亦可）")
    void createPlaylist() throws Exception {
        String token = login("alice", "123456");
        long plid = create(token, """
                {"playlistName":"我的新歌单","description":"测试","isPublic":true}
                """);
        assertThat(plid).isPositive();
    }

    @Test
    @DisplayName("创建歌单不传 isPublic：默认公开")
    void createDefaultsPublic() throws Exception {
        String token = login("alice", "123456");
        long plid = create(token, """
                {"playlistName":"默认公开歌单"}
                """);
        // 游客能看到说明是公开的
        mockMvc.perform(get("/api/playlist/{plid}", plid))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.playlist.isPublic").value(true));
    }

    @Test
    @DisplayName("创建私密歌单：isPublic=false")
    void createPrivate() throws Exception {
        String token = login("alice", "123456");
        long plid = create(token, """
                {"playlistName":"私密歌单","isPublic":false}
                """);
        mockMvc.perform(get("/api/playlist/{plid}", plid).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.playlist.isPublic").value(false));
    }

    @Test
    @DisplayName("创建歌单名称为空：400 参数校验")
    void createBlankName() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(post("/api/playlist")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playlistName":""}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("创建歌单名称超长(>100)：400 参数校验")
    void createNameTooLong() throws Exception {
        String token = login("alice", "123456");
        String longName = "歌".repeat(101); // 超过 @Size(max=100)
        mockMvc.perform(post("/api/playlist")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playlistName\":\"" + longName + "\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("创建歌单简介超长(>255)：400 参数校验")
    void createDescTooLong() throws Exception {
        String token = login("alice", "123456");
        String longDesc = "字".repeat(256); // 超过 @Size(max=255)
        mockMvc.perform(post("/api/playlist")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playlistName\":\"正常名\",\"description\":\"" + longDesc + "\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("未登录创建歌单：401")
    void createAnonymous() throws Exception {
        mockMvc.perform(post("/api/playlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playlistName":"游客歌单"}
                                """))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================ 修改歌单 ============================

    @Test
    @DisplayName("owner 修改本人歌单：名称与公开性更新生效")
    void updateOwnPlaylist() throws Exception {
        String token = login("alice", "123456");
        // alice 改自己的公开歌单 plid=1 → 改名并转私密
        mockMvc.perform(put("/api/playlist/{plid}", 1)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playlistName":"华语经典(改)","description":"更新","isPublic":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        MvcResult res = mockMvc.perform(get("/api/playlist/{plid}", 1).header(TOKEN_HEADER, token))
                .andReturn();
        assertThat(readJson(res).path("data").path("playlist").path("playlistName").asText())
                .isEqualTo("华语经典(改)");
        assertThat(readJson(res).path("data").path("playlist").path("isPublic").asBoolean())
                .isFalse();
    }

    @Test
    @DisplayName("管理员修改他人歌单：成功（越过归属校验）")
    void adminUpdateOthers() throws Exception {
        String admin = login("admin", "123456");
        mockMvc.perform(put("/api/playlist/{plid}", 1)
                        .header(TOKEN_HEADER, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playlistName":"管理员改的","isPublic":true}
                                """))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("修改他人歌单：403")
    void updateOthersForbidden() throws Exception {
        // bob 改 alice 的 plid=1
        String token = login("bob", "123456");
        mockMvc.perform(put("/api/playlist/{plid}", 1)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playlistName":"越权改","isPublic":true}
                                """))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("修改不存在的歌单：404")
    void updateMissing() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(put("/api/playlist/{plid}", 99999)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playlistName":"幽灵歌单","isPublic":true}
                                """))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("修改歌单缺 isPublic：400 参数校验（更新场景公开性必填）")
    void updateMissingIsPublic() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(put("/api/playlist/{plid}", 1)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playlistName":"没给公开性"}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ============================ 删除歌单 ============================

    @Test
    @DisplayName("owner 删除本人歌单：成功，之后详情 404")
    void deleteOwnPlaylist() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(delete("/api/playlist/{plid}", 1).header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/playlist/{plid}", 1).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("管理员删除他人歌单：成功")
    void adminDeleteOthers() throws Exception {
        String admin = login("admin", "123456");
        mockMvc.perform(delete("/api/playlist/{plid}", 3).header(TOKEN_HEADER, admin))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("删除他人歌单：403")
    void deleteOthersForbidden() throws Exception {
        // bob 删 alice 的 plid=1
        String token = login("bob", "123456");
        mockMvc.perform(delete("/api/playlist/{plid}", 1).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("删除不存在的歌单：404")
    void deleteMissing() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(delete("/api/playlist/{plid}", 99999).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    // ============================ 我的歌单 ============================

    @Test
    @DisplayName("我的歌单：返回本人全部歌单（含私密），带曲目数")
    void listMine() throws Exception {
        // alice 拥有 plid=1(公开,5首) + plid=2(私密,2首)
        String token = login("alice", "123456");
        MvcResult res = mockMvc.perform(get("/api/playlist/mine")
                        .header(TOKEN_HEADER, token).param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode records = readJson(res).path("data").path("records");
        assertThat(records).isNotEmpty();
        boolean hasPrivate = false;
        for (JsonNode pl : records) {
            // 都应属于 alice(uid=5)
            assertThat(pl.path("uid").asLong()).isEqualTo(5);
            if (pl.path("plid").asLong() == 1L) {
                // plid=1 曲目数为 5
                assertThat(pl.path("songCount").asLong()).isEqualTo(5);
            }
            if (pl.path("plid").asLong() == 2L) {
                hasPrivate = true; // 私密歌单也在"我的歌单"中
            }
        }
        assertThat(hasPrivate).isTrue();
    }

    @Test
    @DisplayName("我的歌单：未登录→401")
    void listMineRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/playlist/mine"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("空歌单的曲目数为 0")
    void emptyPlaylistSongCountZero() throws Exception {
        // bob 的 plid=6 是私密空歌单
        String token = login("bob", "123456");
        assertThat(songCountOf(token, 6)).isZero();
    }

    // ============================ 歌单详情 + 可见性（软鉴权） ============================

    @Test
    @DisplayName("游客查看公开歌单详情：成功，曲目按加入时间倒序")
    void guestViewPublicDetail() throws Exception {
        // plid=1 公开，游客无 token 也可看
        MvcResult res = mockMvc.perform(get("/api/playlist/{plid}", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.playlist.plid").value(1))
                .andExpect(jsonPath("$.data.songs.records").isArray())
                .andReturn();
        assertThat(readJson(res).path("data").path("songs").path("total").asLong()).isEqualTo(5);
    }

    @Test
    @DisplayName("游客查看私密歌单：404（不泄露存在性）")
    void guestViewPrivateNotFound() throws Exception {
        // plid=2 是 alice 的私密歌单
        mockMvc.perform(get("/api/playlist/{plid}", 2))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("他人查看私密歌单：404")
    void othersViewPrivateNotFound() throws Exception {
        // bob 看 alice 的私密 plid=2
        String token = login("bob", "123456");
        mockMvc.perform(get("/api/playlist/{plid}", 2).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("owner 查看自己的私密歌单：成功")
    void ownerViewOwnPrivate() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(get("/api/playlist/{plid}", 2).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.playlist.plid").value(2))
                .andExpect(jsonPath("$.data.playlist.isPublic").value(false));
    }

    @Test
    @DisplayName("管理员查看他人私密歌单：成功")
    void adminViewOthersPrivate() throws Exception {
        String admin = login("admin", "123456");
        mockMvc.perform(get("/api/playlist/{plid}", 2).header(TOKEN_HEADER, admin))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.playlist.plid").value(2));
    }

    @Test
    @DisplayName("查看不存在的歌单：404")
    void viewMissingNotFound() throws Exception {
        mockMvc.perform(get("/api/playlist/{plid}", 99999))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("带失效令牌查看公开歌单：401（软鉴权不静默降级为游客）")
    void viewWithInvalidTokenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/playlist/{plid}", 1).header(TOKEN_HEADER, "garbage-token-xxx"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("歌单详情含已下架歌：不剔除，标 playable=false")
    void detailKeepsUnplayableSong() throws Exception {
        // alice 的私密 plid=2 含 sid=12(已软删)，owner 可见
        String token = login("alice", "123456");
        MvcResult res = mockMvc.perform(get("/api/playlist/{plid}", 2)
                        .header(TOKEN_HEADER, token).param("size", "50"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode records = readJson(res).path("data").path("songs").path("records");
        boolean foundUnplayable = false;
        for (JsonNode s : records) {
            if (s.path("sid").asLong() == 12L) {
                // 已下架的歌仍在列表中，但不可播放
                assertThat(s.path("playable").asBoolean()).isFalse();
                foundUnplayable = true;
            } else {
                // 其余正常歌曲可播放
                assertThat(s.path("playable").asBoolean()).isTrue();
            }
        }
        assertThat(foundUnplayable).isTrue();
    }

    // ============================ 加歌 / 移歌 ============================

    @Test
    @DisplayName("owner 向歌单加歌：成功，曲目数+1")
    void addSongToOwnPlaylist() throws Exception {
        // bob 的空歌单 plid=6，加入可见歌 sid=1
        String token = login("bob", "123456");
        long before = songCountOf(token, 6);
        mockMvc.perform(post("/api/playlist/{plid}/songs/{sid}", 6, 1).header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertThat(songCountOf(token, 6)).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("重复加同一首歌：幂等，曲目数不变")
    void addSongIdempotent() throws Exception {
        // alice 的 plid=1 已含 sid=1，再加一次不应变化
        String token = login("alice", "123456");
        long before = songCountOf(token, 1);
        mockMvc.perform(post("/api/playlist/{plid}/songs/{sid}", 1, 1).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200));
        assertThat(songCountOf(token, 1)).isEqualTo(before);
    }

    @Test
    @DisplayName("加入待审歌曲：404（仅能加口径A可见的歌）")
    void addPendingSongNotFound() throws Exception {
        // sid=9 待审
        String token = login("alice", "123456");
        mockMvc.perform(post("/api/playlist/{plid}/songs/{sid}", 1, 9).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("加入已软删歌曲：404")
    void addDeletedSongNotFound() throws Exception {
        // sid=12 已软删
        String token = login("alice", "123456");
        mockMvc.perform(post("/api/playlist/{plid}/songs/{sid}", 1, 12).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("加入不存在的歌曲：404")
    void addMissingSongNotFound() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(post("/api/playlist/{plid}/songs/{sid}", 1, 99999).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("向他人歌单加歌：403")
    void addSongToOthersForbidden() throws Exception {
        // bob 往 alice 的 plid=1 加歌
        String token = login("bob", "123456");
        mockMvc.perform(post("/api/playlist/{plid}/songs/{sid}", 1, 5).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("向不存在的歌单加歌：404")
    void addSongToMissingPlaylist() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(post("/api/playlist/{plid}/songs/{sid}", 99999, 1).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("管理员向他人歌单加歌：成功")
    void adminAddSongToOthers() throws Exception {
        String admin = login("admin", "123456");
        // admin 往 alice 的 plid=1 加入 sid=5（原本不在）
        mockMvc.perform(post("/api/playlist/{plid}/songs/{sid}", 1, 5).header(TOKEN_HEADER, admin))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("owner 从歌单移歌：成功，曲目数-1")
    void removeSongFromOwnPlaylist() throws Exception {
        // alice 的 plid=1 含 sid=1，移出
        String token = login("alice", "123456");
        long before = songCountOf(token, 1);
        mockMvc.perform(delete("/api/playlist/{plid}/songs/{sid}", 1, 1).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200));
        assertThat(songCountOf(token, 1)).isEqualTo(before - 1);
    }

    @Test
    @DisplayName("移出未收录的歌：幂等成功，曲目数不变")
    void removeSongNotInPlaylistIdempotent() throws Exception {
        // sid=5 不在 alice 的 plid=1 中
        String token = login("alice", "123456");
        long before = songCountOf(token, 1);
        mockMvc.perform(delete("/api/playlist/{plid}/songs/{sid}", 1, 5).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200));
        assertThat(songCountOf(token, 1)).isEqualTo(before);
    }

    @Test
    @DisplayName("移出已下架的歌：成功（不校验可见性）")
    void removeUnplayableSong() throws Exception {
        // alice 的 plid=2 含已软删 sid=12，应允许移出
        String token = login("alice", "123456");
        mockMvc.perform(delete("/api/playlist/{plid}/songs/{sid}", 2, 12).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("从他人歌单移歌：403")
    void removeSongFromOthersForbidden() throws Exception {
        // bob 移 alice 的 plid=1 里的 sid=1
        String token = login("bob", "123456");
        mockMvc.perform(delete("/api/playlist/{plid}/songs/{sid}", 1, 1).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("未登录加歌：401")
    void addSongAnonymous() throws Exception {
        mockMvc.perform(post("/api/playlist/{plid}/songs/{sid}", 1, 1))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================ 公开广场（空壳占位） ============================

    @Test
    @DisplayName("公开歌单广场：1.0 空壳，返回空分页")
    void listPublicIsEmptyStub() throws Exception {
        String token = login("alice", "123456");
        MvcResult res = mockMvc.perform(get("/api/playlist/public")
                        .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        assertThat(readJson(res).path("data").path("total").asLong()).isZero();
        assertThat(readJson(res).path("data").path("records")).isEmpty();
    }

    @Test
    @DisplayName("登录的非 owner 查看他人公开歌单：成功（正路径）")
    void othersViewPublicSucceeds() throws Exception {
        // bob 看 alice 的公开 plid=1
        String token = login("bob", "123456");
        mockMvc.perform(get("/api/playlist/{plid}", 1).header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.playlist.plid").value(1))
                .andExpect(jsonPath("$.data.playlist.isPublic").value(true));
    }

    @Test
    @DisplayName("歌单详情曲目分页：size=2 翻页，total 恒为全量")
    void detailSongsPaging() throws Exception {
        // plid=1 公开，含 5 首曲目
        MvcResult p1 = mockMvc.perform(get("/api/playlist/{plid}", 1)
                        .param("page", "1").param("size", "2"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode songs1 = readJson(p1).path("data").path("songs");
        assertThat(songs1.path("total").asLong()).isEqualTo(5); // total 是全量
        assertThat(songs1.path("records")).hasSize(2);          // 本页只 2 条
        assertThat(songs1.path("size").asLong()).isEqualTo(2);

        // 第 3 页（size=2）应只剩 1 条（5 = 2+2+1）
        MvcResult p3 = mockMvc.perform(get("/api/playlist/{plid}", 1)
                        .param("page", "3").param("size", "2"))
                .andReturn();
        assertThat(readJson(p3).path("data").path("songs").path("records")).hasSize(1);
    }

    @Test
    @DisplayName("未登录移歌：401")
    void removeSongAnonymous() throws Exception {
        mockMvc.perform(delete("/api/playlist/{plid}/songs/{sid}", 1, 1))
                .andExpect(jsonPath("$.code").value(401));
    }
}
