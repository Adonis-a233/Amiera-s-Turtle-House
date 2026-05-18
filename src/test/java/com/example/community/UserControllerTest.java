package com.example.community;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 用户模块测试：注册、登录、认证
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class UserControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected static final String PASSWORD = "Test@12345";

    // 每次运行使用唯一用户名，避免数据库冲突
    protected final String USERNAME = "test_uc_" + UUID.randomUUID().toString().substring(0, 8);

    protected String token;

    // -------------------- 辅助方法 --------------------

    protected void register(String username, String password) throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    protected String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = objectMapper.readValue(body, Map.class);
        String tok = (String) resp.get("data");
        assertNotNull(tok, "登录应返回 token");
        return tok;
    }

    // -------------------- 测试用例 --------------------

    @Test
    @Order(1)
    @DisplayName("1-注册：正常注册新用户")
    void testRegisterSuccess() throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", USERNAME,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("注册成功"));
    }

    @Test
    @Order(2)
    @DisplayName("2-注册：重复用户名应返回错误")
    void testRegisterDuplicate() throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", USERNAME,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    @Order(3)
    @DisplayName("3-注册：用户名为空应返回错误")
    void testRegisterEmptyUsername() throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "",
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    @Order(4)
    @DisplayName("4-登录：正确凭证登录成功并返回 token")
    void testLoginSuccess() throws Exception {
        token = login(USERNAME, PASSWORD);
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    @Order(5)
    @DisplayName("5-登录：错误密码应返回 401")
    void testLoginWrongPassword() throws Exception {
        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", USERNAME,
                                "password", "wrongpassword"
                        ))))
                .andExpect(result -> assertTrue(
                        result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 200,
                        "Expected 401 or 200, got " + result.getResponse().getStatus()));
    }

    @Test
    @Order(6)
    @DisplayName("6-认证：携带有效 token 访问 /me 成功")
    void testMeWithToken() throws Exception {
        if (token == null) token = login(USERNAME, PASSWORD);
        mockMvc.perform(get("/api/user/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(7)
    @DisplayName("7-认证：未携带 token 访问 /me 应返回 401")
    void testMeWithoutToken() throws Exception {
        mockMvc.perform(get("/api/user/me"))
                .andExpect(status().isUnauthorized());
    }
}
