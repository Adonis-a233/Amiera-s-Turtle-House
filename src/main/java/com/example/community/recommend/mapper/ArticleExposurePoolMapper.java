package com.example.community.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.community.recommend.entity.ArticleExposurePool;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ArticleExposurePoolMapper extends BaseMapper<ArticleExposurePool> {

    @Select("SELECT article_id FROM article_exposure_pool " +
            "WHERE expires_at > NOW() ORDER BY entered_at DESC LIMIT #{limit}")
    List<Long> selectActivePoolIds(@Param("limit") int limit);

    @Update("UPDATE article_exposure_pool SET early_ctr = #{ctr} WHERE article_id = #{articleId}")
    void updateEarlyCtr(@Param("articleId") Long articleId, @Param("ctr") float ctr);

    @Delete("DELETE FROM article_exposure_pool WHERE expires_at <= NOW()")
    void deleteExpired();
}
