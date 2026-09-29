package org.example.blogbackend.vo;

import lombok.Data;
import org.example.blogbackend.entity.Tag;

import java.time.LocalDateTime;

@Data
public class TagVO {
    private Long id;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TagVO from(Tag tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setCreatedAt(tag.getCreatedAt());
        vo.setUpdatedAt(tag.getUpdatedAt());
        return vo;
    }
}
