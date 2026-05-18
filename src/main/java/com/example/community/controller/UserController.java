package com.example.community.controller;

import com.example.community.dto.LoginDTO;
import com.example.community.dto.RegisterDTO;
import com.example.community.dto.UpdatePasswordDTO;
import com.example.community.dto.UpdateUsernameDTO;
import com.example.community.service.UserService;
import com.example.community.utils.Result;
import com.example.community.vo.UserProfileVO;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 用户Controller
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 头像上传路径
     */
    @Value("${file.upload-dir:uploads/avatars}")
    private String uploadDir;

    /**
     * 文件访问前缀
     */
    @Value("${file.access-prefix:http://localhost:8080/uploads/avatars}")
    private String accessPrefix;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginDTO dto) {
        String token = userService.login(dto);
        return Result.success(token);
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody RegisterDTO dto) {
        userService.register(dto);
        return Result.success("注册成功", "注册成功");
    }

    /**
     * 测试认证接口
     */
    @GetMapping("/me")
    public Result<String> me() {
        return Result.success("你已通过 Spring Security 认证");
    }

    /**
     * 获取当前用户个人资料
     */
    @GetMapping("/profile")
    public Result<UserProfileVO> getProfile() {
        return Result.success(userService.getProfile());
    }

    /**
     * 获取指定用户个人资料
     */
    @GetMapping("/profile/{userId}")
    public Result<UserProfileVO> getProfileByUserId(@PathVariable Long userId) {
        return Result.success(userService.getProfile(userId));
    }

    /**
     * 修改用户名
     */
    @PutMapping("/update/username")
    public Result<String> updateUsername(@RequestBody UpdateUsernameDTO dto) {
        userService.updateUsername(dto);
        return Result.success("修改成功", "用户名修改成功");
    }

    /**
     * 修改密码
     */
    @PutMapping("/update/password")
    public Result<String> updatePassword(@RequestBody UpdatePasswordDTO dto) {
        userService.updatePassword(dto);
        return Result.success("修改成功", "密码修改成功");
    }

    /**
     * 修改头像（文件上传方式）
     *
     * [修复] 原代码使用 file.transferTo(new File(dir, filename))
     * 当 uploadDir 为相对路径时，Spring Boot 的 transferTo() 会将文件写到 Servlet 容器的
     * 临时目录（如 /tmp/tomcat.xxx/）而非预期的工作目录，导致头像存储位置错误、
     * 服务器上访问404，但本地因工作目录明确不会暴露此问题。
     *
     * 修复方式：改用 NIO Files.copy()，先将路径 toAbsolutePath() 规范化，
     * 彻底消除相对路径的歧义。同时修复 accessPrefix 末尾可能有多余斜杠导致双斜杠URL的问题。
     */
    @PostMapping("/update/avatar")
    public Result<String> updateAvatar(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("请选择要上传的头像文件");
        }

        // 校验文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.error("只支持上传图片文件");
        }

        // 校验文件大小（限制2MB）
        if (file.getSize() > 2 * 1024 * 1024) {
            return Result.error("头像文件大小不能超过2MB");
        }

        try {
            // [修复] 将配置路径转为绝对路径，避免 transferTo() 相对路径问题
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            // 确保上传目录存在
            Files.createDirectories(uploadPath);

            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = UUID.randomUUID().toString() + extension;

            // [修复] 改用 NIO Files.copy()，彻底避免 transferTo() 在服务器上写到 /tmp 的问题
            Path destPath = uploadPath.resolve(newFilename);
            Files.copy(file.getInputStream(), destPath, StandardCopyOption.REPLACE_EXISTING);

            // [修复] 去掉 accessPrefix 末尾多余的斜杠，避免生成 "http://.../avatars//uuid.png"
            String prefix = accessPrefix.endsWith("/")
                    ? accessPrefix.substring(0, accessPrefix.length() - 1)
                    : accessPrefix;
            String avatarUrl = prefix + "/" + newFilename;

            // 更新用户头像
            userService.updateAvatar(avatarUrl);

            return Result.success("头像修改成功", avatarUrl);
        } catch (IOException e) {
            return Result.error("头像上传失败：" + e.getMessage());
        }
    }

    /**
     * 修改头像（URL方式，适用于前端已自行上传到OSS等场景）
     */
    @PutMapping("/update/avatar")
    public Result<String> updateAvatarByUrl(@RequestBody java.util.Map<String, String> params) {
        String avatarUrl = params.get("avatarUrl");
        userService.updateAvatar(avatarUrl);
        return Result.success("修改成功", "头像修改成功");
    }
}
