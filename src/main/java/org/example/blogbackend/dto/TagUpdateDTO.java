package org.example.blogbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TagUpdateDTO {

    @NotBlank(message = "标签不能为空")
    @Size(max = 50,message = "标签长度不超过50")
    private String name;
}
