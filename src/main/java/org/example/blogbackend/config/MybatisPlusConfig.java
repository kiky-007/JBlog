package org.example.blogbackend.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("org.example.blogbackend.mapper")
public class MybatisPlusConfig {
}
