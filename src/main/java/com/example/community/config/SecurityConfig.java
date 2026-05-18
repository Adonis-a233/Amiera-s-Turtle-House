package com.example.community.config;

import com.example.community.utils.JwtUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Spring Security配置类
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    @Lazy
    private UserDetailsService userDetailsService;

    /**
     * JWT认证过滤器
     * 注意：不能加 @Bean，否则 Spring Boot 会把所有 Filter bean 自动注册进 Servlet 容器
     * 过滤器链，导致该过滤器在 Security 链之外再执行一次（双重注册）。
     */
    private OncePerRequestFilter jwtAuthenticationFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain chain
            ) throws ServletException, IOException {

                String authHeader = request.getHeader("Authorization");

                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);

                    if (jwtUtil.validateToken(token)) {
                        String username = jwtUtil.extractUsername(token);

                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        username, null, null
                                );

                        SecurityContextHolder.getContext()
                                .setAuthentication(authToken);
                    }
                }

                chain.doFilter(request, response);
            }
        };
    }

    /**
     * 密码加密器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 安全过滤器链（核心配置）
     *
     * [修复] 原配置只放行 /api/user/login、/api/user/register 和 /uploads/**，
     * 导致 /api/article/list、/api/article/{id}、/api/article/search 等浏览接口
     * 全部要求登录才能访问，返回401。
     *
     * 作为社区平台，首页文章列表、文章详情、搜索、评论查看这些"只读"操作应当对
     * 未登录用户公开，否则前端首页无法展示任何内容。
     * 写操作（发布、点赞、评论、修改个人信息）仍然需要登录。
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(auth -> auth
                        // 放行：登录注册
                        .requestMatchers(
                                "/api/user/login",
                                "/api/user/register"
                        ).permitAll()

                        // 放行：头像等静态资源
                        .requestMatchers("/uploads/**").permitAll()

                        // 放行：WebSocket 握手路径（鉴权由 JwtHandshakeInterceptor 负责）
                        // Spring Security 授权检查先于 HandshakeInterceptor 执行；若不放行，
                        // 浏览器 WebSocket API 无法携带 Authorization 头，握手会被直接 401 拒绝。
                        .requestMatchers("/ws/**").permitAll()

                        // [修复] 放行：文章只读接口（列表、详情、搜索、按用户查文章）
                        // 未登录用户应能正常浏览社区内容，否则前端首页直接401
                        .requestMatchers(
                                "/api/article/list",
                                "/api/article/search",
                                "/api/article/user/**"
                        ).permitAll()
                        // GET /api/article/{id} 详情页也公开（用 HttpMethod 限定只放行GET）
                        .requestMatchers(
                                org.springframework.http.HttpMethod.GET,
                                "/api/article/**"
                        ).permitAll()

                        // 分享计数无需登录（与 view_count 一致）
                        .requestMatchers("/api/article/share/**").permitAll()

                        // [修复] 放行：评论查看接口（GET），发评论仍需登录（POST走下面的认证）
                        .requestMatchers(
                                org.springframework.http.HttpMethod.GET,
                                "/api/comment/**"
                        ).permitAll()

                        // 放行：搜索接口（文本搜索为GET，图片/混合搜索为POST，均应公开）
                        .requestMatchers("/api/search/**").permitAll()

                        // 帖子审核管理接口，仅 ADMIN 角色可访问
                        .requestMatchers("/api/review/admin/**").hasRole("ADMIN")

                        // 敏感词管理接口，仅 ADMIN 角色可访问
                        .requestMatchers("/api/admin/sensitive/**").hasRole("ADMIN")

                        // 其他接口（写操作）需认证
                        .anyRequest().authenticated()
                )

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":401,\"msg\":\"未认证\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":403,\"msg\":\"无权限\"}");
                        })
                )

                .addFilterBefore(
                        jwtAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
