package org.example.blogbackend.vo;

import lombok.Data;
import org.example.blogbackend.entity.Article;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ArticleVO {

    private Long id;
    private String title;
    private String content;
    private String summary;
    private Integer status;
    private Long categoryId;
    private String categoryName;
    private Long authorId;
    private String authorName;
    private List<TagVO> tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ArticleVO from(Article article, String categoryName, String authorName, List<TagVO> tags) {
        ArticleVO vo = new ArticleVO();
        vo.setId(article.getId());
        vo.setTitle(article.getTitle());
        vo.setContent(article.getContent());
        vo.setSummary(article.getSummary());
        vo.setStatus(article.getStatus());
        vo.setCategoryId(article.getCategoryId());
        vo.setAuthorId(article.getAuthorId());
        vo.setCategoryName(categoryName);
        vo.setAuthorName(authorName);
        vo.setTags(tags);
        vo.setCreatedAt(article.getCreatedAt());
        vo.setUpdatedAt(article.getUpdatedAt());
        return vo;
    }
}
