package org.example.blogbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryCreateDTO {

    @NotBlank(message = "分类不能为空")
    @Size(max = 50,message = "分类名称长度不能超过50")
    private String name;
}
