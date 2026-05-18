package com.example.community.im.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SensitiveWordMapper {

    List<String> selectAllWords();

    List<String> selectWordsPaged(@Param("offset") int offset, @Param("size") int size);

    long countWords();

    void insertWord(@Param("word") String word);

    void deleteWord(@Param("word") String word);
}
