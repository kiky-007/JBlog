package org.example.blogbackend.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.example.blogbackend.dto.TagCreateDTO;
import org.example.blogbackend.dto.TagUpdateDTO;
import org.example.blogbackend.entity.Tag;
import org.example.blogbackend.vo.TagVO;

import java.util.List;

public interface TagService extends IService<Tag> {

    public TagVO create(TagCreateDTO dto);

    public TagVO update(Long id,TagUpdateDTO dto);

    public void deleteById(Long id);

    public TagVO findById(Long id);

    public List<TagVO> findAll();
}
