package org.example.blogbackend.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("article")
public class Article {
    @TableId(value = "id",type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    private String summary;
    private Integer status;
    private Long categoryId;
    private Long authorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
