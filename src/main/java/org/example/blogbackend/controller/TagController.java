package org.example.blogbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.blogbackend.common.result.Result;
import org.example.blogbackend.dto.TagCreateDTO;
import org.example.blogbackend.dto.TagUpdateDTO;
import org.example.blogbackend.service.TagService;
import org.example.blogbackend.vo.TagVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @PostMapping
    public Result<TagVO> create(@Valid @RequestBody TagCreateDTO dto) {
        return Result.success(tagService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<TagVO> update(@PathVariable Long id,@Valid @RequestBody TagUpdateDTO dto) {
        return Result.success(tagService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {   //"?"为通配符,可以任意类型，没有就为null
        tagService.deleteById(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<TagVO> get(@PathVariable Long id) {
        return Result.success(tagService.findById(id));
    }

    @GetMapping
    public Result<List<TagVO>> getAll() {
        return Result.success(tagService.findAll());
    }
}
