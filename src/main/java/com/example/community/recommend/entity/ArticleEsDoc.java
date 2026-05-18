package com.example.community.recommend.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.List;

/**
 * ES 中的帖子文档，仅存推荐所需字段。
 * dense_vector mapping 由 EsIndexInitializer 在启动时创建，createIndex=false 避免覆盖。
 */
@Data
@Document(indexName = "article_recommend", createIndex = false)
public class ArticleEsDoc {

    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long articleId;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    /** 文本 embedding，1024 维，qwen3-vl-embedding 生成。 */
    private List<Double> textVector;

    /** 图片 embedding（封面图），可选。 */
    private List<Double> imageVector;
}
