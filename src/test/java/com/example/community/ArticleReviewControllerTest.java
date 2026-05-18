package com.example.community;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.community.entity.Article;
import com.example.community.entity.User;
import com.example.community.mapper.ArticleMapper;
import com.example.community.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 帖子审核模块测试：待审核列表、审核通过、审核拒绝
 *
 * 注意：
 *   - 审核接口要求 ROLE_ADMIN，且 Service 层会调用 getCurrentUserId()（依赖 Principal 为 String）。
 *   - 本测试在 @BeforeAll 中向数据库注册管理员账号并设置 role=ADMIN；
 *     请求时使用 SecurityMockMvcRequestPostProcessors.authentication() 注入带 ROLE_ADMIN 权限的
 *     UsernamePasswordAuthenticationToken（Principal 为管理员用户名字符串），
 *     同时满足 hasRole("ADMIN") 校验与 getCurrentUserId() 的 String principal 要求。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ArticleReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ArticleMapper articleMapper;

    private final String ADMIN_USERNAME = "test_admin_" + UUID.randomUUID().toString().substring(0, 6);
    private final String PASSWORD = "Test@12345";

    /** 供 approve/reject 测试使用的帖子 ID（若能获取到） */
    private Long pendingArticleId;

    /** 构造带 ROLE_ADMIN 且 Principal 为 String 的认证后处理器 */
    private org.springframework.test.web.servlet.request.RequestPostProcessor adminAuth() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                ADMIN_USERNAME,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ROLE_USER"))
        );
        return authentication(auth);
    }

    @BeforeAll
    void setUp() throws Exception {
        // 1. 注册管理员账号
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", ADMIN_USERNAME, "password", PASSWORD
                        ))))
                .andExpect(status().isOk());

        // 2. 将该账号角色更新为 ADMIN（Spring Security 角色校验依赖此字段）
        User adminUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, ADMIN_USERNAME));
        assertNotNull(adminUser, "管理员用户应已注册到数据库");
        adminUser.setRole("ADMIN");
        userMapper.updateById(adminUser);

        // 3. 发布一篇帖子并强制设为待人工审核（reviewStatus=3），供 approve 测试使用
        String setupAuthor = "test_ra_" + UUID.randomUUID().toString().substring(0, 6);
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", setupAuthor, "password", PASSWORD
                        ))))
                .andExpect(status().isOk());
        MvcResult setupLogin = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", setupAuthor, "password", PASSWORD
                        ))))
                .andReturn();
        @SuppressWarnings("unchecked")
        String setupToken = (String) objectMapper.readValue(
                setupLogin.getResponse().getContentAsString(), Map.class).get("data");
        mockMvc.perform(post("/api/article/publish")
                        .header("Authorization", "Bearer " + setupToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "审核测试帖_" + UUID.randomUUID().toString().substring(0, 6),
                                "content", "此帖子用于审核通过测试",
                                "isDraft", false
                        ))))
                .andExpect(status().isOk());
        User setupUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, setupAuthor));
        if (setupUser != null) {
            articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                    .eq(Article::getUserId, setupUser.getId())
                    .set(Article::getReviewStatus, 3));
        }
        fetchPendingArticleId();
    }

    @Test
    @Order(1)
    @DisplayName("1-未登录访问待审核列表应返回 401 或 403")
    void testPendingListUnauthorized() throws Exception {
        mockMvc.perform(get("/api/review/admin/pending"))
                .andExpect(result -> assertTrue(
                        result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403,
                        "Expected 401 or 403, got " + result.getResponse().getStatus()));
    }

    @Test
    @Order(2)
    @DisplayName("2-管理员查询待人工审核帖子列表")
    void testGetPendingList() throws Exception {
        mockMvc.perform(get("/api/review/admin/pending")
                        .param("page", "1").param("size", "20")
                        .with(adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(3)
    @DisplayName("3-管理员审核通过帖子")
    void testApprove() throws Exception {
        if (pendingArticleId == null) {
            Assumptions.assumeTrue(false, "无待审核帖子，跳过 approve 测试");
        }
        mockMvc.perform(post("/api/review/admin/approve/" + pendingArticleId)
                        .with(adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(4)
    @DisplayName("4-管理员审核拒绝帖子（含拒绝原因）")
    void testReject() throws Exception {
        // 发布一篇新帖子用于拒绝测试
        // 先注册一个普通用户并发帖
        String authorName = "test_review_author_" + UUID.randomUUID().toString().substring(0, 6);
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", authorName, "password", PASSWORD
                        ))))
                .andExpect(status().isOk());

        MvcResult rAuthor = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", authorName, "password", PASSWORD
                        ))))
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> authorLogin = objectMapper.readValue(
                rAuthor.getResponse().getContentAsString(), Map.class);
        String authorToken = (String) authorLogin.get("data");

        // 发帖
        mockMvc.perform(post("/api/article/publish")
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "待拒绝测试帖",
                                "content", "此帖子将被拒绝",
                                "isDraft", false
                        ))))
                .andExpect(status().isOk());

        // 强制设为待人工审核，再刷新 pendingArticleId
        User rejectAuthor = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, authorName));
        if (rejectAuthor != null) {
            articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                    .eq(Article::getUserId, rejectAuthor.getId())
                    .set(Article::getReviewStatus, 3));
        }
        fetchPendingArticleId();
        if (pendingArticleId == null) {
            Assumptions.assumeTrue(false, "无待审核帖子，跳过 reject 测试");
        }

        mockMvc.perform(post("/api/review/admin/reject/" + pendingArticleId)
                        .with(adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reason", "内容不符合社区规范"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(5)
    @DisplayName("5-普通用户（无 ADMIN 角色）访问审核接口应返回 403")
    void testApproveAsRegularUser() throws Exception {
        // 注册普通用户并用其认证
        String regularUser = "test_regular_" + UUID.randomUUID().toString().substring(0, 6);
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", regularUser, "password", PASSWORD
                        ))))
                .andExpect(status().isOk());

        // 用普通 JWT 访问（JWT filter 不传 authorities，hasRole 会失败）
        MvcResult r = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", regularUser, "password", PASSWORD
                        ))))
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> loginResp = objectMapper.readValue(r.getResponse().getContentAsString(), Map.class);
        String regularToken = (String) loginResp.get("data");

        mockMvc.perform(get("/api/review/admin/pending")
                        .header("Authorization", "Bearer " + regularToken))
                .andExpect(result -> assertTrue(
                        result.getResponse().getStatus() == 403 || result.getResponse().getStatus() == 401,
                        "Expected 403 or 401, got " + result.getResponse().getStatus()));
    }

    private void fetchPendingArticleId() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/review/admin/pending")
                        .param("page", "1").param("size", "5")
                        .with(adminAuth()))
                .andReturn();
        if (r.getResponse().getStatus() != 200) return;
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = objectMapper.readValue(r.getResponse().getContentAsString(), Map.class);
        Object dataObj = resp.get("data");
        if (dataObj == null) return;
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) dataObj;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("list");
        if (records != null && !records.isEmpty()) {
            pendingArticleId = Long.valueOf(records.get(0).get("id").toString());
        }
    }
}
