package org.example.blogbackend.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.example.blogbackend.common.result.PageResult;
import org.example.blogbackend.common.result.Result;
import org.example.blogbackend.dto.ArticleCreateDTO;
import org.example.blogbackend.dto.ArticleUpdateDTO;
import org.example.blogbackend.service.ArticleService;
import org.example.blogbackend.vo.ArticleListVO;
import org.example.blogbackend.vo.ArticleVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/articles")
@RequiredArgsConstructor
@Validated   //★ 开启方法参数校验：下面 page/size 上的 @Min/@Max 才会生效
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
            //@RequestParam绑定查询参数?key=value
            //@Min/@Max：防止 ?size=100000 这种请求把数据库打爆（深分页 + 大页 = 灾难）
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") Long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") Long size,
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
