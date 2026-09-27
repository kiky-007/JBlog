package org.example.blogbackend.vo;

import lombok.Data;
import org.example.blogbackend.entity.Category;

import java.time.LocalDateTime;

@Data
public class CategoryVO {
    private Long id;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CategoryVO from(Category category) {
    CategoryVO vo = new CategoryVO();
    vo.setId(category.getId());
    vo.setName(category.getName());
    vo.setCreatedAt(category.getCreatedAt());
    vo.setUpdatedAt(category.getUpdatedAt());
    return vo;
    }
}
