package org.example.blogbackend.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.blogbackend.common.result.Result;
import org.example.blogbackend.dto.CategoryCreateDTO;
import org.example.blogbackend.dto.CategoryUpdateDTO;
import org.example.blogbackend.service.CategoryService;
import org.example.blogbackend.vo.CategoryVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor  //注入
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public Result<CategoryVO> create(@Valid @RequestBody CategoryCreateDTO dto) {   //@Valid校验
        return  Result.success(categoryService.create(dto));
    }

    @GetMapping("/{id}")
    public Result<CategoryVO> getDetaolById(@PathVariable Long id) {  //@PathVariable指定名称的参数给控制器中的形参赋值
        return Result.success(categoryService.getDetailById(id));
    }

    @GetMapping
    public Result<List<CategoryVO>> getAll() {
        return Result.success(categoryService.getAll());
    }

    @PutMapping("/{id}")
    public Result<CategoryVO> update(@PathVariable Long id,@Valid @RequestBody CategoryUpdateDTO dto) {
        return Result.success(categoryService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success();
    }
}
