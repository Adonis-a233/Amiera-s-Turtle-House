package com.example.community;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 文章模块测试：发布、列表、详情、点赞、按用户查询
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ArticleControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected final String USERNAME = "test_ac_" + UUID.randomUUID().toString().substring(0, 8);
    protected final String PASSWORD = "Test@12345";
    protected String token;
    protected Long userId;

    /** 供子测试类复用的已发布帖子 ID */
    protected Long publishedArticleId;

    @BeforeAll
    void setUp() throws Exception {
        // 注册
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", USERNAME, "password", PASSWORD
                        ))))
                .andExpect(status().isOk());

        // 登录
        MvcResult r = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", USERNAME, "password", PASSWORD
                        ))))
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = objectMapper.readValue(r.getResponse().getContentAsString(), Map.class);
        token = (String) resp.get("data");
        assertNotNull(token);

        // 获取 userId
        MvcResult profileR = mockMvc.perform(get("/api/user/profile")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> profileResp = objectMapper.readValue(
                profileR.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) profileResp.get("data");
        userId = Long.valueOf(data.get("id").toString());
    }

    @Test
    @Order(1)
    @DisplayName("1-发布文章（正式帖，含图片和标签）")
    void testPublishArticle() throws Exception {
        Map<String, Object> body = Map.of(
                "title", "测试帖子：" + UUID.randomUUID().toString().substring(0, 6),
                "content", "这是一篇测试帖子的正文内容，描述了一道美食的制作方法。",
                "isDraft", false,
                "imageUrls", List.of("https://example.com/img/a.jpg"),
                "tags", List.of("美食", "测试")
        );
        MvcResult result = mockMvc.perform(post("/api/article/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        // 获取刚发布的帖子 ID（通过用户帖子列表查询）
        fetchLatestArticleId();
    }

    @Test
    @Order(2)
    @DisplayName("2-发布草稿")
    void testPublishDraft() throws Exception {
        Map<String, Object> body = Map.of(
                "title", "草稿帖子：" + UUID.randomUUID().toString().substring(0, 6),
                "content", "这是一篇未发布的草稿内容。",
                "isDraft", true
        );
        mockMvc.perform(post("/api/article/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(3)
    @DisplayName("3-未登录不能发布文章（应返回 401）")
    void testPublishWithoutToken() throws Exception {
        mockMvc.perform(post("/api/article/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "无权发布",
                                "content", "未登录"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    @DisplayName("4-获取文章列表（最新排序，无需登录）")
    void testGetListByNew() throws Exception {
        mockMvc.perform(get("/api/article/list")
                        .param("page", "1")
                        .param("size", "10")
                        .param("sortType", "new"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @Order(5)
    @DisplayName("5-获取文章列表（热门排序）")
    void testGetListByHot() throws Exception {
        mockMvc.perform(get("/api/article/list")
                        .param("page", "1")
                        .param("size", "10")
                        .param("sortType", "hot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(6)
    @DisplayName("6-获取文章详情（无需登录）")
    void testGetDetail() throws Exception {
        if (publishedArticleId == null) {
            Assumptions.assumeTrue(false, "publishedArticleId 未初始化，跳过");
        }
        mockMvc.perform(get("/api/article/" + publishedArticleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists());
    }

    @Test
    @Order(7)
    @DisplayName("7-点赞文章（需要登录）")
    void testLikeArticle() throws Exception {
        if (publishedArticleId == null) {
            Assumptions.assumeTrue(false, "publishedArticleId 未初始化，跳过");
        }
        mockMvc.perform(post("/api/article/like/" + publishedArticleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(8)
    @DisplayName("8-再次点赞（取消点赞）")
    void testUnlikeArticle() throws Exception {
        if (publishedArticleId == null) {
            Assumptions.assumeTrue(false, "publishedArticleId 未初始化，跳过");
        }
        mockMvc.perform(post("/api/article/like/" + publishedArticleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(9)
    @DisplayName("9-点赞无需登录应返回 401")
    void testLikeWithoutToken() throws Exception {
        if (publishedArticleId == null) {
            Assumptions.assumeTrue(false, "publishedArticleId 未初始化，跳过");
        }
        mockMvc.perform(post("/api/article/like/" + publishedArticleId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(10)
    @DisplayName("10-根据用户 ID 获取其发布的文章列表")
    void testGetUserArticles() throws Exception {
        mockMvc.perform(get("/api/article/user/" + userId)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /** 通过查询用户帖子列表找到最新帖子 ID */
    private void fetchLatestArticleId() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/article/user/" + userId)
                        .param("page", "1").param("size", "5")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = objectMapper.readValue(r.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.get("data");
        if (data != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
            if (records != null && !records.isEmpty()) {
                publishedArticleId = Long.valueOf(records.get(0).get("id").toString());
            }
        }
    }
}
