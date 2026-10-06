package org.example.blogbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ArticleUpdateDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题长度不能超过 200")
    private String title;

    @NotBlank(message = "正文不能为空")
    private String content;

    @Size(max = 500, message = "摘要长度不能超过 500")
    private String summary;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    private Integer status;

    private List<Long> tagIds;
}
