package com.music.song;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 歌曲审核模块 API 集成测试（管理后台 {@code /api/admin/song}，整类 role=2）。
 *
 * <p>覆盖待审列表与审核（通过/驳回）；验证仅待审歌可审、重复审核 409、
 * 驳回必填理由、通过清空历史驳回理由、非管理员 403。</p>
 *
 * <p>种子（03_seed.sql）：待审 sid=9(All Too Well)、sid=10(Lover)，audit_status=0；
 * 已通过 sid=2(晴天) audit_status=1；已驳回 sid=11 audit_status=2；软删 sid=12。
 * admin(role=2)、uploader_jay(role=1)、alice(role=0)，密码均 123456。</p>
 */
class SongAuditApiTest extends AbstractIntegrationTest {

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
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    private JsonNode readJson(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    // ============================ 待审列表 ============================

    @Test
    @DisplayName("管理员查待审列表：含种子待审歌(sid=9/10)，已审/软删不在其中")
    void listPending() throws Exception {
        String token = login("admin", "123456");
        MvcResult res = mockMvc.perform(get("/api/admin/song/pending").header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode records = readJson(res).path("data").path("records");
        assertThat(records).isNotEmpty();
        for (JsonNode s : records) {
            // 待审列表里每首都应是 audit_status=0
            assertThat(s.path("auditStatus").asInt()).isEqualTo(0);
        }
        // 至少包含种子里的两首待审歌
        boolean hasSeed = false;
        for (JsonNode s : records) {
            long sid = s.path("sid").asLong();
            if (sid == 9 || sid == 10) {
                hasSeed = true;
                break;
            }
        }
        assertThat(hasSeed).isTrue();
    }

    @Test
    @DisplayName("非管理员(上传者)查待审列表：403")
    void listPendingAsUploaderForbidden() throws Exception {
        String token = login("uploader_jay", "123456");
        mockMvc.perform(get("/api/admin/song/pending").header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("普通用户查待审列表：403")
    void listPendingAsNormalForbidden() throws Exception {
        String token = login("alice", "123456");
        mockMvc.perform(get("/api/admin/song/pending").header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("未登录查待审列表：401")
    void listPendingAnonymous() throws Exception {
        mockMvc.perform(get("/api/admin/song/pending"))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================ 审核通过 ============================

    @Test
    @DisplayName("审核通过待审歌：成功，之后进入公开可见(口径A)")
    void auditPass() throws Exception {
        String token = login("admin", "123456");
        // sid=9 待审 → 通过
        mockMvc.perform(post("/api/admin/song/{sid}/audit", 9)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pass":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 通过后可作为公开歌曲详情访问（口径A）
        mockMvc.perform(get("/api/song/public/{sid}", 9).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.auditStatus").value(1));
    }

    @Test
    @DisplayName("审核通过会清空历史驳回理由：驳回→再审不可（需待审），此处验证通过分支清 remark")
    void auditPassClearsRemark() throws Exception {
        String token = login("admin", "123456");
        // sid=10 待审 → 通过，remark 应为空
        mockMvc.perform(post("/api/admin/song/{sid}/audit", 10)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pass":true,"remark":"忽略我"}
                                """))
                .andExpect(jsonPath("$.code").value(200));

        MvcResult res = mockMvc.perform(get("/api/song/public/{sid}", 10).header(TOKEN_HEADER, token))
                .andReturn();
        // 通过分支强制清空 auditRemark（即便请求带了 remark 也忽略）
        JsonNode remark = readJson(res).path("data").path("auditRemark");
        assertThat(remark.isNull() || remark.asText().isEmpty()).isTrue();
    }

    // ============================ 审核驳回 ============================

    @Test
    @DisplayName("驳回待审歌并填理由：成功")
    void auditReject() throws Exception {
        String token = login("admin", "123456");
        mockMvc.perform(post("/api/admin/song/{sid}/audit", 9)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pass":false,"remark":"内容不符合规范"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 驳回后不进入口径A（公开详情 404）
        mockMvc.perform(get("/api/song/public/{sid}", 9).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("驳回不填理由：400（驳回理由必填，Service 层显式抛 BAD_REQUEST）")
    void auditRejectWithoutRemark() throws Exception {
        String token = login("admin", "123456");
        mockMvc.perform(post("/api/admin/song/{sid}/audit", 9)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pass":false}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ============================ 边界与校验 ============================

    @Test
    @DisplayName("审核结论 pass 字段缺失：400 参数校验")
    void auditMissingPass() throws Exception {
        String token = login("admin", "123456");
        mockMvc.perform(post("/api/admin/song/{sid}/audit", 9)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"remark":"没给结论"}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("重复审核已通过的歌：409")
    void auditAlreadyApproved() throws Exception {
        String token = login("admin", "123456");
        // sid=2 已是 audit_status=1
        mockMvc.perform(post("/api/admin/song/{sid}/audit", 2)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pass":true}
                                """))
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("重复审核已驳回的歌：409")
    void auditAlreadyRejected() throws Exception {
        String token = login("admin", "123456");
        // sid=11 已是 audit_status=2
        mockMvc.perform(post("/api/admin/song/{sid}/audit", 11)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pass":true}
                                """))
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("审核不存在的歌：404")
    void auditMissingSong() throws Exception {
        String token = login("admin", "123456");
        mockMvc.perform(post("/api/admin/song/{sid}/audit", 99999)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pass":true}
                                """))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("审核已软删的歌：404")
    void auditDeletedSong() throws Exception {
        String token = login("admin", "123456");
        // sid=12 is_deleted=true
        mockMvc.perform(post("/api/admin/song/{sid}/audit", 12)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pass":true}
                                """))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("非管理员审核：403")
    void auditAsUploaderForbidden() throws Exception {
        String token = login("uploader_jay", "123456");
        mockMvc.perform(post("/api/admin/song/{sid}/audit", 9)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pass":true}
                                """))
                .andExpect(jsonPath("$.code").value(403));
    }

    // ============================ 校验与分支补充 ============================

    @Test
    @DisplayName("驳回歌取播放地址：404（驳回状态独立坐实）")
    void getPlayUrlRejectedNotFound() throws Exception {
        // sid=11 audit_status=2 驳回
        String token = login("alice", "123456");
        mockMvc.perform(get("/api/song/public/{sid}/play-url", 11).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("驳回理由超长(>255)：400")
    void auditRemarkTooLong() throws Exception {
        String token = login("admin", "123456");
        String longRemark = "理".repeat(256);
        mockMvc.perform(post("/api/admin/song/{sid}/audit", 9)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pass\":false,\"remark\":\"" + longRemark + "\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("驳回理由为纯空白串：400（Service 层 isBlank 校验）")
    void auditRejectBlankRemark() throws Exception {
        // @Size 拦不住空白串，走 Service 的 isBlank() 分支
        String token = login("admin", "123456");
        mockMvc.perform(post("/api/admin/song/{sid}/audit", 9)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pass":false,"remark":"   "}
                                """))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ============================ 管理端歌曲全量列表 ============================

    @Test
    @DisplayName("管理端歌曲全量列表:含各审核态 + 软删歌,isDeleted 字段在")
    void listAllForAdminContainsAll() throws Exception {
        String token = login("admin", "123456");
        MvcResult res = mockMvc.perform(get("/api/admin/song/all").header(TOKEN_HEADER, token).param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode records = readJson(res).path("data").path("records");
        assertThat(records.isArray()).isTrue();
        Set<Long> sids = new HashSet<>();
        boolean hasIsDeletedField = false;
        for (JsonNode s : records) {
            sids.add(s.path("sid").asLong());
            if (s.has("isDeleted")) {
                hasIsDeletedField = true;
            }
        }
        assertThat(sids).contains(11L, 12L); // 驳回 + 软删都在(区别 listPending/listPublic)
        assertThat(hasIsDeletedField).isTrue();
    }

    @Test
    @DisplayName("管理端歌曲列表 keyword 筛选")
    void listAllForAdminKeyword() throws Exception {
        String token = login("admin", "123456");
        MvcResult res = mockMvc.perform(get("/api/admin/song/all")
                        .header(TOKEN_HEADER, token).param("keyword", "晴天").param("size", "50"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode records = readJson(res).path("data").path("records");
        for (JsonNode s : records) {
            assertThat(s.path("title").asText()).contains("晴天");
        }
    }

    @Test
    @DisplayName("管理端歌曲列表 auditStatus 筛选(仅驳回)")
    void listAllForAdminAuditStatus() throws Exception {
        String token = login("admin", "123456");
        MvcResult res = mockMvc.perform(get("/api/admin/song/all")
                        .header(TOKEN_HEADER, token).param("auditStatus", "2").param("size", "50"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode records = readJson(res).path("data").path("records");
        for (JsonNode s : records) {
            assertThat(s.path("auditStatus").asInt()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("管理端歌曲列表:非管理员 403")
    void listAllForAdminForbidden() throws Exception {
        String token = login("alice", "123456"); // role=0
        mockMvc.perform(get("/api/admin/song/all").header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(403));
    }
}
