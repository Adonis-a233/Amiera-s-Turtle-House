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
 * 联系人模块测试：关注、取消关注、拉黑、取消拉黑、好友列表
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ContactControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // 用户 A：发起关注/拉黑的一方
    private final String USERNAME_A = "test_con_a_" + UUID.randomUUID().toString().substring(0, 6);
    // 用户 B：被关注/拉黑的一方
    private final String USERNAME_B = "test_con_b_" + UUID.randomUUID().toString().substring(0, 6);
    private final String PASSWORD = "Test@12345";

    private String tokenA;
    private Long userIdB;

    @BeforeAll
    void setUp() throws Exception {
        // 注册用户 A
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", USERNAME_A, "password", PASSWORD
                        ))))
                .andExpect(status().isOk());

        // 注册用户 B
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", USERNAME_B, "password", PASSWORD
                        ))))
                .andExpect(status().isOk());

        // 用户 A 登录
        MvcResult r = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", USERNAME_A, "password", PASSWORD
                        ))))
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> loginResp = objectMapper.readValue(r.getResponse().getContentAsString(), Map.class);
        tokenA = (String) loginResp.get("data");
        assertNotNull(tokenA);

        // 用户 B 登录以获取其 ID
        MvcResult rB = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", USERNAME_B, "password", PASSWORD
                        ))))
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> loginRespB = objectMapper.readValue(rB.getResponse().getContentAsString(), Map.class);
        String tokenB = (String) loginRespB.get("data");

        MvcResult profileB = mockMvc.perform(get("/api/user/profile")
                        .header("Authorization", "Bearer " + tokenB))
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> profileResp = objectMapper.readValue(profileB.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> profileData = (Map<String, Object>) profileResp.get("data");
        userIdB = Long.valueOf(profileData.get("id").toString());
    }

    @Test
    @Order(1)
    @DisplayName("1-关注用户 B")
    void testFollow() throws Exception {
        mockMvc.perform(post("/api/contact/follow/" + userIdB)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(2)
    @DisplayName("2-查看好友列表（互相关注才是好友）")
    void testGetFriends() throws Exception {
        mockMvc.perform(get("/api/contact/friends")
                        .param("page", "1").param("size", "20")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(3)
    @DisplayName("3-关注：未登录应返回 401")
    void testFollowWithoutToken() throws Exception {
        mockMvc.perform(post("/api/contact/follow/" + userIdB))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    @DisplayName("4-取消关注用户 B")
    void testUnfollow() throws Exception {
        mockMvc.perform(delete("/api/contact/follow/" + userIdB)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(5)
    @DisplayName("5-拉黑用户 B")
    void testBlock() throws Exception {
        mockMvc.perform(post("/api/contact/block/" + userIdB)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(6)
    @DisplayName("6-拉黑：未登录应返回 401")
    void testBlockWithoutToken() throws Exception {
        mockMvc.perform(post("/api/contact/block/" + userIdB))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(7)
    @DisplayName("7-取消拉黑用户 B")
    void testUnblock() throws Exception {
        mockMvc.perform(delete("/api/contact/block/" + userIdB)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(8)
    @DisplayName("8-好友列表分页参数校验")
    void testGetFriendsPagination() throws Exception {
        mockMvc.perform(get("/api/contact/friends")
                        .param("page", "2").param("size", "5")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
