package com.example.community.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.community.recommend.entity.UserBehavior;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserBehaviorMapper extends BaseMapper<UserBehavior> {

    @Select("SELECT COUNT(*) FROM user_behavior WHERE user_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);

    @Select("SELECT article_id FROM user_behavior " +
            "WHERE user_id = #{userId} AND event_type IN (3,5) AND article_id > 0 " +
            "GROUP BY article_id")
    List<Long> selectLikedOrCollectedArticleIds(@Param("userId") Long userId);
}
