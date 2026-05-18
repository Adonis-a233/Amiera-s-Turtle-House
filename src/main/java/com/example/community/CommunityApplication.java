package com.example.community;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
@MapperScan(
    basePackages = {
        "com.example.community.mapper",
        "com.example.community.recommend.mapper",
        "com.example.community.im.mapper"
    },
    annotationClass = Mapper.class
)
public class CommunityApplication {

    public static void main(String[] args) {
        // [修复] 强制设置JVM时区为Asia/Shanghai
        // 原因：Linux服务器JVM默认时区为UTC，导致LocalDateTime.now()生成的时间戳
        // 比实际北京时间少8小时（例如存入DB的createTime、updateTime全部偏差）
        // 本机不会复现，因为本机JVM时区通常跟随系统设为CST(+8)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));

        SpringApplication.run(CommunityApplication.class, args);

    }

}
