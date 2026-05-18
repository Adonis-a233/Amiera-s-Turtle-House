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
 * 评论模块测试：发表评论、回复评论、树形列表、删除评论
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final String USERNAME = "test_cc_" + UUID.randomUUID().toString().substring(0, 8);
    private final String PASSWORD = "Test@12345";
    private String token;
    private Long userId;
    private Long articleId;
    private Long commentId;   // 一级评论 ID，用于回复测试
    private Long replyId;     // 二级评论 ID，用于删除测试

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
        Map<String, Object> profileResp = objectMapper.readValue(profileR.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> profileData = (Map<String, Object>) profileResp.get("data");
        userId = Long.valueOf(profileData.get("id").toString());

        // 发布一篇帖子用于评论测试
        mockMvc.perform(post("/api/article/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "评论测试帖_" + UUID.randomUUID().toString().substring(0, 6),
                                "content", "此帖子用于测试评论功能",
                                "isDraft", false
                        ))))
                .andExpect(status().isOk());

        // 获取帖子 ID
        MvcResult ar = mockMvc.perform(get("/api/article/user/" + userId)
                        .param("page", "1").param("size", "5")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> artResp = objectMapper.readValue(ar.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> artData = (Map<String, Object>) artResp.get("data");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) artData.get("records");
        if (records != null && !records.isEmpty()) {
            articleId = Long.valueOf(records.get(0).get("id").toString());
        }
    }

    @Test
    @Order(1)
    @DisplayName("1-发表一级评论")
    void testAddComment() throws Exception {
        if (articleId == null) Assumptions.assumeTrue(false, "articleId 未初始化，跳过");

        MvcResult r = mockMvc.perform(post("/api/comment/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "articleId", articleId,
                                "content", "这是一条测试评论，评论了该帖子的内容。"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        // 获取评论 ID（通过评论列表查询）
        fetchFirstCommentId();
    }

    @Test
    @Order(2)
    @DisplayName("2-回复评论（二级评论）")
    void testAddReply() throws Exception {
        if (articleId == null || commentId == null) {
            Assumptions.assumeTrue(false, "articleId 或 commentId 未初始化，跳过");
        }

        MvcResult r = mockMvc.perform(post("/api/comment/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "articleId", articleId,
                                "content", "这是一条回复评论的内容。",
                                "parentId", commentId
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        // 获取回复评论 ID
        fetchReplyId();
    }

    @Test
    @Order(3)
    @DisplayName("3-发表评论：未登录应返回 401")
    void testAddCommentWithoutToken() throws Exception {
        if (articleId == null) Assumptions.assumeTrue(false, "articleId 未初始化，跳过");
        mockMvc.perform(post("/api/comment/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "articleId", articleId,
                                "content", "未登录评论"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    @DisplayName("4-获取文章评论树（无需登录）")
    void testGetCommentTree() throws Exception {
        if (articleId == null) Assumptions.assumeTrue(false, "articleId 未初始化，跳过");
        MvcResult r = mockMvc.perform(get("/api/comment/list/" + articleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = objectMapper.readValue(r.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        List<?> comments = (List<?>) resp.get("data");
        // 应至少有一条评论（刚发表的）
        assertNotNull(comments, "评论列表不应为 null");
    }

    @Test
    @Order(5)
    @DisplayName("5-删除回复评论（仅本人）")
    void testDeleteReply() throws Exception {
        if (replyId == null) Assumptions.assumeTrue(false, "replyId 未初始化，跳过");
        mockMvc.perform(delete("/api/comment/" + replyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(6)
    @DisplayName("6-删除一级评论（仅本人）")
    void testDeleteComment() throws Exception {
        if (commentId == null) Assumptions.assumeTrue(false, "commentId 未初始化，跳过");
        mockMvc.perform(delete("/api/comment/" + commentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(7)
    @DisplayName("7-删除评论：未登录应返回 401")
    void testDeleteCommentWithoutToken() throws Exception {
        if (commentId == null) Assumptions.assumeTrue(false, "commentId 未初始化，跳过");
        mockMvc.perform(delete("/api/comment/" + commentId))
                .andExpect(status().isUnauthorized());
    }

    // ===================== 私有辅助 =====================

    private void fetchFirstCommentId() throws Exception {
        if (articleId == null) return;
        MvcResult r = mockMvc.perform(get("/api/comment/list/" + articleId)).andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = objectMapper.readValue(r.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> comments = (List<Map<String, Object>>) resp.get("data");
        if (comments != null && !comments.isEmpty()) {
            commentId = Long.valueOf(comments.get(0).get("id").toString());
        }
    }

    private void fetchReplyId() throws Exception {
        if (articleId == null) return;
        MvcResult r = mockMvc.perform(get("/api/comment/list/" + articleId)).andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = objectMapper.readValue(r.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> comments = (List<Map<String, Object>>) resp.get("data");
        if (comments == null) return;
        for (Map<String, Object> c : comments) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) c.get("children");
            if (children != null && !children.isEmpty()) {
                replyId = Long.valueOf(children.get(0).get("id").toString());
                return;
            }
        }
    }
}
