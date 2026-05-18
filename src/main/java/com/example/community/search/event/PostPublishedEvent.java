package com.example.community.search.event;

import com.example.community.entity.Article;
import org.springframework.context.ApplicationEvent;

/**
 * 帖子发布事件。
 * ArticleServiceImpl 在帖子状态变为"已发布"时发布此事件，
 * PostIndexService 监听并异步写入 ES。
 */
public class PostPublishedEvent extends ApplicationEvent {

    private final Article article;

    public PostPublishedEvent(Object source, Article article) {
        super(source);
        this.article = article;
    }

    public Article getArticle() {
        return article;
    }
}
