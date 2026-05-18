package com.example.community.recommend.mapper;

import com.example.community.recommend.entity.ArticleEsDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ArticleEsRepository extends ElasticsearchRepository<ArticleEsDoc, String> {
}
