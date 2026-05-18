package com.example.community.service;

import com.example.community.utils.PageResult;
import com.example.community.vo.ContactVO;

public interface ContactService {

    void follow(Long followeeId);

    void unfollow(Long followeeId);

    void block(Long blockedId);

    void unblock(Long blockedId);

    PageResult<ContactVO> getFriends(int page, int size);

    boolean isBlocked(Long userA, Long userB);
}
