package org.example.blogbackend.vo;

import lombok.Data;
import org.example.blogbackend.entity.Article;

import java.time.LocalDateTime;

@Data
public class ArticleListVO {

    private Long id;
    private String title;
    private String summary;
    private Integer status;
    private Long categoryId;
    private String categoryName;
    private Long authorId;
    private String authorName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ArticleListVO from(Article article, String categoryName, String authorName) {
        ArticleListVO vo = new ArticleListVO();
        vo.setId(article.getId());
        vo.setTitle(article.getTitle());
        vo.setSummary(article.getSummary());
        vo.setStatus(article.getStatus());
        vo.setCategoryId(article.getCategoryId());
        vo.setCategoryName(categoryName);
        vo.setAuthorId(article.getAuthorId());
        vo.setAuthorName(authorName);
        vo.setCreatedAt(article.getCreatedAt());
        vo.setUpdatedAt(article.getUpdatedAt());
        return vo;
    }
}
