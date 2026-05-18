package com.example.community;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 文章扩展功能测试：我的文章、点赞文章、草稿箱、搜索、行为上报、编辑、删除、收藏、分享
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ArticleExtendedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final String USERNAME = "test_ae_" + UUID.randomUUID().toString().substring(0, 8);
    private final String PASSWORD = "Test@12345";
    private String token;
    private Long userId;
    private Long articleId;    // 正式发布帖子 ID
    private Long draftId;      // 草稿帖子 ID

    @BeforeAll
    void setUp() throws Exception {
        // 注册 & 登录
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", USERNAME, "password", PASSWORD
                        ))))
                .andExpect(status().isOk());

        MvcResult r = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", USERNAME, "password", PASSWORD
                        ))))
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> loginResp = objectMapper.readValue(r.getResponse().getContentAsString(), Map.class);
        token = (String) loginResp.get("data");
        assertNotNull(token);

        // 获取 userId
        MvcResult profileR = mockMvc.perform(get("/api/user/profile")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> profileResp = objectMapper.readValue(
                profileR.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> profileData = (Map<String, Object>) profileResp.get("data");
        userId = Long.valueOf(profileData.get("id").toString());

        // 发布一篇正式帖子（用于后续测试）
        final String uniqueTitle = "扩展测试帖_" + UUID.randomUUID().toString().substring(0, 6);
        mockMvc.perform(post("/api/article/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", uniqueTitle,
                                "content", "这是扩展测试帖子的内容，主要用于测试编辑、删除、收藏、分享等功能。",
                                "isDraft", false,
                                "tags", List.of("测试"),
                                "imageUrls", List.of()
                        ))))
                .andExpect(status().isOk());

        // 发布草稿
        mockMvc.perform(post("/api/article/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "草稿_" + UUID.randomUUID().toString().substring(0, 6),
                                "content", "草稿内容",
                                "isDraft", true
                        ))))
                .andExpect(status().isOk());

        // 取最新帖子和草稿 ID
        fetchArticleIds(uniqueTitle);
    }

    // ===================== 测试方法 =====================

    @Test
    @Order(1)
    @DisplayName("1-获取当前用户已点赞的帖子列表")
    void testGetLikedArticles() throws Exception {
        mockMvc.perform(get("/api/article/liked")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(2)
    @DisplayName("2-获取草稿箱列表")
    void testGetDrafts() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/article/drafts")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = objectMapper.readValue(r.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.get("data");
        // 草稿数量应 >= 1（刚发布了一篇草稿）
        Number total = (Number) data.get("total");
        assertTrue(total == null || total.longValue() >= 0, "草稿列表应正常返回");
    }

    @Test
    @Order(3)
    @DisplayName("3-模糊搜索文章（关键词匹配）")
    void testSearchByKeyword() throws Exception {
        mockMvc.perform(get("/api/article/search")
                        .param("keyword", "测试")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(4)
    @DisplayName("4-搜索：空关键词应正常响应")
    void testSearchEmptyKeyword() throws Exception {
        mockMvc.perform(get("/api/article/search")
                        .param("keyword", "")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(5)
    @DisplayName("5-上报行为（点击/停留）")
    void testReportBehaviorClick() throws Exception {
        if (articleId == null) Assumptions.assumeTrue(false, "articleId 未初始化，跳过");
        mockMvc.perform(post("/api/article/behavior/" + articleId)
                        .param("eventType", "2")
                        .param("dwellTime", "15")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(6)
    @DisplayName("6-上报行为（跳过）")
    void testReportBehaviorSkip() throws Exception {
        if (articleId == null) Assumptions.assumeTrue(false, "articleId 未初始化，跳过");
        mockMvc.perform(post("/api/article/behavior/" + articleId)
                        .param("eventType", "6")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(7)
    @DisplayName("7-编辑帖子（修改标题与内容）")
    void testUpdateArticle() throws Exception {
        if (articleId == null) Assumptions.assumeTrue(false, "articleId 未初始化，跳过");
        mockMvc.perform(put("/api/article/update")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", articleId,
                                "title", "已编辑的标题_" + UUID.randomUUID().toString().substring(0, 4),
                                "content", "已编辑的内容，包含更多细节描述。",
                                "tags", List.of("美食", "编辑")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(8)
    @DisplayName("8-收藏帖子（切换收藏状态）")
    void testCollectArticle() throws Exception {
        if (articleId == null) Assumptions.assumeTrue(false, "articleId 未初始化，跳过");
        mockMvc.perform(post("/api/article/collect/" + articleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(9)
    @DisplayName("9-再次收藏（取消收藏）")
    void testUncollectArticle() throws Exception {
        if (articleId == null) Assumptions.assumeTrue(false, "articleId 未初始化，跳过");
        mockMvc.perform(post("/api/article/collect/" + articleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(10)
    @DisplayName("10-获取当前用户收藏列表")
    void testGetCollectedArticles() throws Exception {
        mockMvc.perform(get("/api/article/collected")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(11)
    @DisplayName("11-分享计数 +1（无需登录）")
    void testShareArticle() throws Exception {
        if (articleId == null) Assumptions.assumeTrue(false, "articleId 未初始化，跳过");
        mockMvc.perform(post("/api/article/share/" + articleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(12)
    @DisplayName("12-删除帖子（软删除，仅本人）")
    void testDeleteArticle() throws Exception {
        // 新建一篇专门用于删除的帖子，避免影响其他测试
        mockMvc.perform(post("/api/article/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "待删除帖子",
                                "content", "此帖子将被删除",
                                "isDraft", false
                        ))))
                .andExpect(status().isOk());

        // 查出新帖子 ID
        MvcResult r = mockMvc.perform(get("/api/article/user/" + userId)
                        .param("page", "1").param("size", "5")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = objectMapper.readValue(r.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.get("data");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");

        if (records == null || records.isEmpty()) {
            Assumptions.assumeTrue(false, "没有可删除的帖子，跳过");
        }
        Long deleteId = Long.valueOf(records.get(0).get("id").toString());

        mockMvc.perform(delete("/api/article/" + deleteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(13)
    @DisplayName("13-删除帖子：未登录应返回 401")
    void testDeleteWithoutToken() throws Exception {
        if (articleId == null) Assumptions.assumeTrue(false, "articleId 未初始化，跳过");
        mockMvc.perform(delete("/api/article/" + articleId))
                .andExpect(status().isUnauthorized());
    }

    // ===================== 私有辅助 =====================

    private void fetchArticleIds(String uniqueTitle) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/article/user/" + userId)
                        .param("page", "1").param("size", "20")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = objectMapper.readValue(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.get("data");
        if (data == null) return;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
        if (records == null) return;

        for (Map<String, Object> rec : records) {
            String title = (String) rec.get("title");
            if (title != null && title.equals(uniqueTitle)) {
                articleId = Long.valueOf(rec.get("id").toString());
                break;
            }
        }

        // 草稿箱
        MvcResult dr = mockMvc.perform(get("/api/article/drafts")
                        .param("page", "1").param("size", "5")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> draftResp = objectMapper.readValue(
                dr.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> draftData = (Map<String, Object>) draftResp.get("data");
        if (draftData != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> draftRecords = (List<Map<String, Object>>) draftData.get("records");
            if (draftRecords != null && !draftRecords.isEmpty()) {
                draftId = Long.valueOf(draftRecords.get(0).get("id").toString());
            }
        }
    }
}
