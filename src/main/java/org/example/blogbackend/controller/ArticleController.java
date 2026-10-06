package org.example.blogbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.blogbackend.common.result.PageResult;
import org.example.blogbackend.common.result.Result;
import org.example.blogbackend.dto.ArticleCreateDTO;
import org.example.blogbackend.dto.ArticleUpdateDTO;
import org.example.blogbackend.service.ArticleService;
import org.example.blogbackend.vo.ArticleListVO;
import org.example.blogbackend.vo.ArticleVO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @PostMapping
    public Result<ArticleVO> create(@Valid @RequestBody ArticleCreateDTO dto) {
        return Result.success(articleService.create(dto));
    }

    @GetMapping("/{id}")
    public Result<ArticleVO> getDetail(@PathVariable Long id) {
        return Result.success(articleService.getDetailById(id));
    }

    @GetMapping
    public Result<PageResult<ArticleListVO>> page(
            @RequestParam(defaultValue = "1") Long page,   //@RequestParam绑定查询参数?key=value
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) Long categoryId, //属性required是否必传，不传为null
            @RequestParam(required = false) Integer status) {
        return Result.success(articleService.pageList(page, size, categoryId, status));
    }

    @PutMapping("/{id}")
    public Result<ArticleVO> update(@PathVariable Long id, @Valid @RequestBody ArticleUpdateDTO dto) {
        return Result.success(articleService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        articleService.delete(id);
        return Result.success();
    }
}
