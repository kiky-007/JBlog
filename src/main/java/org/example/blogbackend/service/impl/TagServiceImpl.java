package org.example.blogbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.example.blogbackend.common.exception.BusinessException;
import org.example.blogbackend.common.result.ResultCode;
import org.example.blogbackend.dto.TagCreateDTO;
import org.example.blogbackend.dto.TagUpdateDTO;
import org.example.blogbackend.entity.ArticleTag;
import org.example.blogbackend.entity.Tag;
import org.example.blogbackend.mapper.ArticleTagMapper;
import org.example.blogbackend.mapper.TagMapper;
import org.example.blogbackend.service.TagService;
import org.example.blogbackend.vo.TagVO;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
@RequiredArgsConstructor
public class TagServiceImpl extends ServiceImpl<TagMapper,Tag> implements TagService {

    private final ArticleTagMapper articleTagMapper;

    @Override
    public TagVO create(TagCreateDTO dto) {
        if(existsByName(dto.getName(),null)){
            throw new BusinessException(ResultCode.CONFLICT);
        }
        Tag tag = new Tag();
        tag.setName(dto.getName()); //把 DTO 里的 name 值，拷贝到 Tag 对象的字段上
        save(tag);  //添加，把 Tag 对象持久化到数据库（INSERT）
        return TagVO.from(getById(tag.getId()));  //TagVO.from(tag)是从内存里拿值。要重查从数据库拿值，createdAt和updatedAt是数据库自动更新的
    }                            //重查：把数据库生成的/刚被数据库改掉的列，重新读回内存对象

    @Override
    public TagVO update(Long id,TagUpdateDTO dto) {
        Tag  tag = getById(id);
        if(tag==null){
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if(existsByName(dto.getName(),id)){
            throw new BusinessException(ResultCode.CONFLICT);
        }
        tag.setName(dto.getName());
        updateById(tag);
        return TagVO.from(getById(tag.getId()));
    }

    @Override
    public void deleteById(Long id) {
        if(getById(id)==null){
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        Long refCount = articleTagMapper.selectCount(
                new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getTagId, id)
        );
        if(refCount>0){
            throw new BusinessException(ResultCode.CONFLICT);
        }
        removeById(id);
    }

    @Override
    public TagVO findById(Long id) {
        Tag tag = getById(id);
        if(tag==null){
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return TagVO.from(tag);
    }

    @Override
    public List<TagVO> findAll() {
        return list().stream().map(TagVO::from).toList();
    }


    private boolean existsByName(String name,Long excludeId) {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<Tag>()
                .eq(Tag::getName, name);
        if(excludeId!=null){
            wrapper.ne(Tag::getId,excludeId);
        }
        return count(wrapper) > 0;
    }
}
