package com.example.community.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.community.dto.LoginDTO;
import com.example.community.dto.RegisterDTO;
import com.example.community.dto.UpdatePasswordDTO;
import com.example.community.dto.UpdateUsernameDTO;
import com.example.community.entity.User;
import com.example.community.vo.UserProfileVO;

/**
 * 用户Service接口
 */
public interface UserService extends IService<User> {

    /**
     * 用户登录
     */
    String login(LoginDTO dto);

    /**
     * 用户注册
     */
    void register(RegisterDTO dto);

    /**
     * 获取当前登录用户的个人资料
     */
    UserProfileVO getProfile();

    /**
     * 获取指定用户的个人资料
     */
    UserProfileVO getProfile(Long userId);

    /**
     * 修改用户名
     */
    void updateUsername(UpdateUsernameDTO dto);

    /**
     * 修改密码
     */
    void updatePassword(UpdatePasswordDTO dto);

    /**
     * 修改头像（传入头像URL或路径）
     */
    void updateAvatar(String avatarUrl);

    /**
     * 根据用户名获取用户（辅助方法）
     */
    User getByUsername(String username);

    /**
     * 获取当前登录用户ID
     */
    Long getCurrentUserId();
}
