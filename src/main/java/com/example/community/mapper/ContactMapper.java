package com.example.community.mapper;

import com.example.community.vo.ContactVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ContactMapper {

    int follow(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    void unfollow(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    boolean isFollowing(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    void block(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    void unblock(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    boolean isBlocked(@Param("userA") Long userA, @Param("userB") Long userB);

    /** 检查 userA 和 userB 是否互相关注（双向 follow 同时存在） */
    boolean isMutualFollow(@Param("userA") Long userA, @Param("userB") Long userB);

    List<ContactVO> selectFriends(@Param("userId") Long userId,
                                  @Param("offset") int offset,
                                  @Param("pageSize") int pageSize);

    long countFriends(@Param("userId") Long userId);

    void updateFollowingCount(@Param("userId") Long userId, @Param("delta") int delta);

    void updateFollowerCount(@Param("userId") Long userId, @Param("delta") int delta);
}
