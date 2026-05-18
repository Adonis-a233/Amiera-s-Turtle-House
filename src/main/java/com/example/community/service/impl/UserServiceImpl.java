package com.example.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.community.dto.LoginDTO;
import com.example.community.dto.RegisterDTO;
import com.example.community.dto.UpdatePasswordDTO;
import com.example.community.dto.UpdateUsernameDTO;
import com.example.community.entity.Article;
import com.example.community.entity.User;
import com.example.community.mapper.ArticleMapper;
import com.example.community.mapper.UserMapper;
import com.example.community.service.UserService;
import com.example.community.utils.JwtUtil;
import com.example.community.vo.UserProfileVO;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户 Service 实现类
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService, UserDetailsService {

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    @Lazy
    private AuthenticationManager authenticationManager;

    @Resource
    private ArticleMapper articleMapper;

    /**
     * Spring Security 加载用户
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = this.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));

        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }

        String role = (user.getRole() != null && !user.getRole().isBlank())
                ? user.getRole() : "USER";
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_" + role))
        );
    }

    /**
     * 用户登录
     */
    @Override
    public String login(LoginDTO dto) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        dto.getUsername(),
                        dto.getPassword()
                )
        );

        return jwtUtil.generateToken(dto.getUsername());
    }

    /**
     * 用户注册
     */
    @Override
    public void register(RegisterDTO dto) {
        if (dto.getUsername() == null || dto.getUsername().trim().isEmpty()) {
            throw new RuntimeException("用户名不能为空");
        }

        if (dto.getPassword() == null || dto.getPassword().trim().isEmpty()) {
            throw new RuntimeException("密码不能为空");
        }

        long count = this.count(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));

        if (count > 0) {
            throw new RuntimeException("用户名已存在");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));

        // 新增字段初始化：新用户默认值
        user.setLevel(1);
        user.setCoins(0);
        user.setFollowerCount(0);
        user.setFollowingCount(0);
        user.setPostCount(0);

        this.save(user);
    }

    /**
     * 获取当前登录用户 ID
     */
    @Override
    public Long getCurrentUserId() {
        String username = (String) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        User user = this.getByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户未登录或不存在");
        }
        return user.getId();
    }

    /**
     * 根据用户名获取用户
     */
    @Override
    public User getByUsername(String username) {
        return this.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
    }

    /**
     * 获取当前登录用户的个人资料
     */
    @Override
    public UserProfileVO getProfile() {
        Long userId = getCurrentUserId();
        return getProfile(userId);
    }

    /**
     * 获取指定用户的个人资料
     */
    @Override
    public UserProfileVO getProfile(Long userId) {
        User user = this.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        UserProfileVO vo = new UserProfileVO();
        // BeanUtils 会自动复制同名字段：level、coins、followerCount、followingCount
        // postCount 也会被复制，但下方会用实时 COUNT 查询结果覆盖
        BeanUtils.copyProperties(user, vo);

        // 实时查询已发布帖子数量（覆盖 BeanUtils 复制的冗余值，保证准确）
        //
        // 设计说明：User.postCount 是冗余缓存字段，用于在其他场景（如帖子列表）
        // 快速展示作者发帖数，避免 JOIN 查询。这里用实时 COUNT 是为了个人主页
        // 数据绝对准确。两种方式并不矛盾。
        long postCount = articleMapper.selectCount(
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getUserId, userId)
                        .eq(Article::getStatus, 1)
        );
        // [修改] setArticleCount -> setPostCount（UserProfileVO 字段已改名）
        vo.setPostCount((int) postCount);

        // 查询获赞总数
        Integer totalLikes = articleMapper.selectTotalLikesByUserId(userId);
        vo.setTotalLikes(totalLikes != null ? totalLikes : 0);

        return vo;
    }

    /**
     * 修改用户名
     */
    @Override
    public void updateUsername(UpdateUsernameDTO dto) {
        if (dto.getNewUsername() == null || dto.getNewUsername().trim().isEmpty()) {
            throw new RuntimeException("新用户名不能为空");
        }

        long count = this.count(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getNewUsername()));
        if (count > 0) {
            throw new RuntimeException("用户名已存在");
        }

        Long userId = getCurrentUserId();
        User user = this.getById(userId);
        user.setUsername(dto.getNewUsername());
        this.updateById(user);
    }

    /**
     * 修改密码
     */
    @Override
    public void updatePassword(UpdatePasswordDTO dto) {
        if (dto.getOldPassword() == null || dto.getOldPassword().trim().isEmpty()) {
            throw new RuntimeException("旧密码不能为空");
        }
        if (dto.getNewPassword() == null || dto.getNewPassword().trim().isEmpty()) {
            throw new RuntimeException("新密码不能为空");
        }

        Long userId = getCurrentUserId();
        User user = this.getById(userId);

        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("旧密码错误");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        this.updateById(user);
    }

    /**
     * 修改头像
     */
    @Override
    public void updateAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            throw new RuntimeException("头像地址不能为空");
        }

        Long userId = getCurrentUserId();
        User user = this.getById(userId);
        user.setAvatar(avatarUrl);
        this.updateById(user);
    }
}
