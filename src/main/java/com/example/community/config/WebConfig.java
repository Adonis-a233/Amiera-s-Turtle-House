package com.example.community.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web配置类
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir:uploads/avatars}")
    private String uploadDir;

    /**
     * 配置跨域
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 配置静态资源映射（用于访问上传的头像文件）
     *
     * [修复] 原代码直接拼接 "file:" + uploadDir + "/"，当uploadDir为相对路径时
     * （yaml默认值 "uploads/avatars"），服务器上路径会相对于JVM工作目录（通常是/root）
     * 而非项目目录，导致头像资源404。
     * 修复方式：将路径转为绝对路径再传给Spring，保证本地和服务器行为一致。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将配置的路径统一转为绝对路径，消除工作目录差异
        String absoluteUploadDir = Paths.get(uploadDir).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/uploads/avatars/**")
                .addResourceLocations("file:" + absoluteUploadDir + "/");
    }
}
