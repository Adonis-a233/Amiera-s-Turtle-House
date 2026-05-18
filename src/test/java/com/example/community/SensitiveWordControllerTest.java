package com.example.community;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.community.entity.User;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 敏感词管理模块测试：查看列表、添加、删除（均需 ADMIN 角色）
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SensitiveWordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    private final String ADMIN_USERNAME = "test_sw_admin_" + UUID.randomUUID().toString().substring(0, 6);
    private final String PASSWORD = "Test@12345";

    /** 测试用的敏感词，带随机后缀避免与已有词冲突 */
    private final String TEST_WORD = "测试敏感词_" + UUID.randomUUID().toString().substring(0, 4);

    private RequestPostProcessor adminAuth() {
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
        // 注册管理员并设置角色
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", ADMIN_USERNAME, "password", PASSWORD
                        ))))
                .andExpect(status().isOk());

        User adminUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, ADMIN_USERNAME));
        assertNotNull(adminUser);
        adminUser.setRole("ADMIN");
        userMapper.updateById(adminUser);
    }

    @Test
    @Order(1)
    @DisplayName("1-未登录访问敏感词列表应返回 401 或 403")
    void testListUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/sensitive/list"))
                .andExpect(result -> assertTrue(
                        result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403,
                        "Expected 401 or 403, got " + result.getResponse().getStatus()));
    }

    @Test
    @Order(2)
    @DisplayName("2-普通用户访问敏感词接口应返回 403")
    void testListAsRegularUser() throws Exception {
        String regularUser = "test_sw_regular_" + UUID.randomUUID().toString().substring(0, 6);
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", regularUser, "password", PASSWORD
                        ))))
                .andExpect(status().isOk());
        var loginR = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", regularUser, "password", PASSWORD
                        ))))
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> loginResp = objectMapper.readValue(loginR.getResponse().getContentAsString(), Map.class);
        String regularToken = (String) loginResp.get("data");

        mockMvc.perform(get("/api/admin/sensitive/list")
                        .header("Authorization", "Bearer " + regularToken))
                .andExpect(result -> assertTrue(
                        result.getResponse().getStatus() == 403 || result.getResponse().getStatus() == 401,
                        "Expected 403 or 401, got " + result.getResponse().getStatus()));
    }

    @Test
    @Order(3)
    @DisplayName("3-管理员查看敏感词列表（分页）")
    void testListSensitiveWords() throws Exception {
        mockMvc.perform(get("/api/admin/sensitive/list")
                        .param("page", "1").param("size", "20")
                        .with(adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @Order(4)
    @DisplayName("4-管理员添加敏感词")
    void testAddSensitiveWord() throws Exception {
        mockMvc.perform(post("/api/admin/sensitive/add")
                        .with(adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("word", TEST_WORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(5)
    @DisplayName("5-添加后查询列表，确认新词已存在")
    void testListAfterAdd() throws Exception {
        var r = mockMvc.perform(get("/api/admin/sensitive/list")
                        .param("page", "1").param("size", "100")
                        .with(adminAuth()))
                .andExpect(status().isOk())
                .andReturn();
        String body = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains(TEST_WORD), "添加后列表中应包含 " + TEST_WORD);
    }

    @Test
    @Order(6)
    @DisplayName("6-管理员删除敏感词")
    void testRemoveSensitiveWord() throws Exception {
        mockMvc.perform(delete("/api/admin/sensitive/remove")
                        .with(adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("word", TEST_WORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(7)
    @DisplayName("7-删除后查询列表，确认词已移除")
    void testListAfterRemove() throws Exception {
        var r = mockMvc.perform(get("/api/admin/sensitive/list")
                        .param("page", "1").param("size", "100")
                        .with(adminAuth()))
                .andExpect(status().isOk())
                .andReturn();
        String body = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(body.contains(TEST_WORD), "删除后列表中不应包含 " + TEST_WORD);
    }

    @Test
    @Order(8)
    @DisplayName("8-敏感词列表分页参数校验（第 2 页）")
    void testListPage2() throws Exception {
        mockMvc.perform(get("/api/admin/sensitive/list")
                        .param("page", "2").param("size", "10")
                        .with(adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}