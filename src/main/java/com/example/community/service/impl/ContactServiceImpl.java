package com.example.community.service.impl;

import com.example.community.mapper.ContactMapper;
import com.example.community.service.ContactService;
import com.example.community.service.UserService;
import com.example.community.utils.PageResult;
import com.example.community.vo.ContactVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ContactServiceImpl implements ContactService {

    @Resource
    private ContactMapper contactMapper;

    @Resource
    private UserService userService;

    @Override
    @Transactional
    public void follow(Long followeeId) {
        Long currentUserId = userService.getCurrentUserId();
        // INSERT IGNORE 本身是原子操作：成功返回1，已存在返回0，无 TOCTOU 窗口
        int inserted = contactMapper.follow(currentUserId, followeeId);
        if (inserted == 0) {
            throw new RuntimeException("已关注");
        }
        contactMapper.updateFollowingCount(currentUserId, 1);
        contactMapper.updateFollowerCount(followeeId, 1);
    }

    @Override
    @Transactional
    public void unfollow(Long followeeId) {
        Long currentUserId = userService.getCurrentUserId();
        if (!contactMapper.isFollowing(currentUserId, followeeId)) {
            throw new RuntimeException("未关注");
        }
        contactMapper.unfollow(currentUserId, followeeId);
        contactMapper.updateFollowingCount(currentUserId, -1);
        contactMapper.updateFollowerCount(followeeId, -1);
    }

    @Override
    @Transactional
    public void block(Long blockedId) {
        Long currentUserId = userService.getCurrentUserId();
        contactMapper.block(currentUserId, blockedId);
        if (contactMapper.isFollowing(currentUserId, blockedId)) {
            contactMapper.unfollow(currentUserId, blockedId);
            contactMapper.updateFollowingCount(currentUserId, -1);
            contactMapper.updateFollowerCount(blockedId, -1);
        }
        if (contactMapper.isFollowing(blockedId, currentUserId)) {
            contactMapper.unfollow(blockedId, currentUserId);
            contactMapper.updateFollowingCount(blockedId, -1);
            contactMapper.updateFollowerCount(currentUserId, -1);
        }
    }

    @Override
    @Transactional
    public void unblock(Long blockedId) {
        Long currentUserId = userService.getCurrentUserId();
        contactMapper.unblock(currentUserId, blockedId);
    }

    @Override
    public PageResult<ContactVO> getFriends(int page, int size) {
        Long currentUserId = userService.getCurrentUserId();
        int offset = (page - 1) * size;
        List<ContactVO> list = contactMapper.selectFriends(currentUserId, offset, size);
        long total = contactMapper.countFriends(currentUserId);
        return new PageResult<>(total, list);
    }

    @Override
    public boolean isBlocked(Long userA, Long userB) {
        return contactMapper.isBlocked(userA, userB);
    }
}
