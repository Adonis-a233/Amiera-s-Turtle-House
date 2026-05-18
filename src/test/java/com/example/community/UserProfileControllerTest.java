package com.example.community;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 用户资料与设置模块测试：查看/修改资料、修改用户名、修改密码、上传头像
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class UserProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final String USERNAME = "test_up_" + UUID.randomUUID().toString().substring(0, 8);
    private final String PASSWORD = "Test@12345";
    private String token;
    private Long userId;

    @BeforeAll
    void setUp() throws Exception {
        // 注册
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", USERNAME,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk());

        // 登录获取 token
        MvcResult result = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", USERNAME,
                                "password", PASSWORD
                        ))))
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        token = (String) resp.get("data");
        assertNotNull(token, "setUp: 登录 token 不应为 null");
    }

    @Test
    @Order(1)
    @DisplayName("1-获取当前用户资料")
    void testGetMyProfile() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/user/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value(USERNAME))
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.get("data");
        userId = Long.valueOf(data.get("id").toString());
    }

    @Test
    @Order(2)
    @DisplayName("2-根据用户 ID 获取指定用户资料")
    void testGetProfileByUserId() throws Exception {
        if (userId == null) {
            Assumptions.assumeTrue(false, "userId 未初始化，跳过此测试");
        }
        mockMvc.perform(get("/api/user/profile/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(userId.toString()));
    }

    @Test
    @Order(3)
    @DisplayName("3-修改用户名（成功）")
    void testUpdateUsernameSuccess() throws Exception {
        String newUsername = USERNAME + "_new";
        mockMvc.perform(put("/api/user/update/username")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "newUsername", newUsername
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 用新用户名重新登录
        MvcResult result = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", newUsername,
                                "password", PASSWORD
                        ))))
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        token = (String) resp.get("data");
        assertNotNull(token, "用新用户名登录应成功");
    }

    @Test
    @Order(4)
    @DisplayName("4-修改密码（成功）")
    void testUpdatePasswordSuccess() throws Exception {
        String newPassword = "NewPass@9999";
        mockMvc.perform(put("/api/user/update/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "oldPassword", PASSWORD,
                                "newPassword", newPassword
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(5)
    @DisplayName("5-修改密码：旧密码错误应返回错误")
    void testUpdatePasswordWrongOld() throws Exception {
        mockMvc.perform(put("/api/user/update/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "oldPassword", "wrongOldPassword",
                                "newPassword", "AnyNewPass@1"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    @Order(6)
    @DisplayName("6-上传头像（multipart 文件方式）")
    void testUploadAvatar() throws Exception {
        byte[] imageBytes = createMinimalPng();

        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", imageBytes);

        mockMvc.perform(multipart("/api/user/update/avatar")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(7)
    @DisplayName("7-上传头像：空文件应返回错误")
    void testUploadAvatarEmptyFile() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0]);

        mockMvc.perform(multipart("/api/user/update/avatar")
                        .file(emptyFile)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    @Order(8)
    @DisplayName("8-修改头像（URL 方式）")
    void testUpdateAvatarByUrl() throws Exception {
        mockMvc.perform(put("/api/user/update/avatar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "avatarUrl", "https://example.com/avatar/test.png"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /** 生成最小有效 1×1 PNG 字节，作为降级方案 */
    private byte[] createMinimalPng() {
        // 最小 1×1 像素白色 PNG（标准 PNG 字节序列）
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG 签名
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,         // IHDR chunk
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,         // 宽1 高1
                0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,  // 8bit RGB
                (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, // IDAT
                0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xCF,
                (byte) 0xC0, 0x00, 0x00, 0x00, 0x02, 0x00, 0x01,
                (byte) 0xE2, 0x21, (byte) 0xBC, 0x33,
                0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,         // IEND
                (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
    }
}
