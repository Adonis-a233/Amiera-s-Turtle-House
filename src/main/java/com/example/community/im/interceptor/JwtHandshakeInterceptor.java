package com.example.community.im.interceptor;

import com.example.community.im.service.IpLocationService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket 握手拦截器（Ticket 版）。
 *
 * 客户端先 POST /api/im/ticket 换取30秒有效的一次性 ticket，
 * 再以 ws://host/ws?ticket=xxx 建立连接。ticket 在此处被原子 GETDEL 消费，
 * 不可重复使用，Nginx 日志中出现的 ticket 也无法还原 JWT 内容。
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IpLocationService ipLocationService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        URI uri = request.getURI();
        String query = uri.getQuery();
        if (query == null) return false;

        String ticket = null;
        for (String param : query.split("&")) {
            if (param.startsWith("ticket=")) {
                ticket = param.substring("ticket=".length());
                break;
            }
        }
        if (ticket == null) return false;

        // 原子 GET + DELETE：ticket 消费后立即失效，防止重放
        String username = stringRedisTemplate.opsForValue().getAndDelete("im:ticket:" + ticket);
        if (username == null) return false;

        attributes.put("username", username);

        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            String ip = ipLocationService.getRealIp(httpRequest);
            String location = ipLocationService.getLocation(ip);
            attributes.put("userIp", ip);
            attributes.put("userLocation", location);
        }

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }
}
