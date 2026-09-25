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
@TableName("category")
public class Category {
    @TableId(value = "id",type = IdType.AUTO)
    private Long id; //Long包装类可以是null,数据库对应是BIGINT
    private String name;
    private LocalDateTime careatedAt;
    private LocalDateTime updatedAt;
}
