package com.example.community.im.controller;

import com.example.community.utils.Result;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 连接换票接口。
 *
 * 背景：浏览器 WebSocket API 不支持自定义 Header，只能把凭证放 URL，
 * 导致 JWT（含用户身份）出现在 Nginx access log 和浏览器历史记录中。
 *
 * 解决方案：客户端先用正常 JWT（Authorization: Bearer xxx）调用此接口，
 * 换取一个30秒有效、一次性使用的随机 ticket，WebSocket 连接时携带 ?ticket=xxx。
 * ticket 是随机 UUID，即使出现在日志中，也无法还原用户身份，且30秒后自动失效。
 */
@RestController
@RequestMapping("/api/im")
public class ImTicketController {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @PostMapping("/ticket")
    public Result<String> issueTicket() {
        String username = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        String ticket = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set("im:ticket:" + ticket, username, 30, TimeUnit.SECONDS);
        return Result.success(ticket);
    }
}
