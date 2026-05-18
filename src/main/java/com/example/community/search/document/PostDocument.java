package com.example.community.search.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ES 索引文档，对应 posts 索引。
 *
 * textVector  : 1024 维，由 qwen3-vl-embedding 对 (title + summary + tags) 编码。
 * imageVector : 1024 维，由 qwen3-vl-embedding 对封面图编码；无图帖子该字段为 null。
 *
 * 两个向量均在 qwen3-vl 的多模态对齐空间中，支持跨模态余弦相似度比较。
 * createIndex = false：Mapping 由 PostsIndexInitializer 在启动时创建。
 */
@Data
@Document(indexName = "posts", createIndex = false)
public class PostDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
    private String content;

    @Field(type = FieldType.Keyword)
    private String coverImageUrl;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    @Field(type = FieldType.Long)
    private Long userId;

    @Field(type = FieldType.Date)
    private LocalDateTime publishedAt;

    /** 文本向量，1024 维，qwen3-vl-embedding 生成。 */
    private float[] textVector;

    /** 图片向量，1024 维，qwen3-vl-embedding 生成；无图帖子为 null。 */
    private float[] imageVector;
}
