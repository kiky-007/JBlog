package org.example.blogbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.example.blogbackend.common.exception.BusinessException;
import org.example.blogbackend.common.result.ResultCode;
import org.example.blogbackend.dto.CategoryCreateDTO;
import org.example.blogbackend.dto.CategoryUpdateDTO;
import org.example.blogbackend.entity.Article;
import org.example.blogbackend.entity.Category;
import org.example.blogbackend.mapper.ArticleMapper;
import org.example.blogbackend.mapper.CategoryMapper;
import org.example.blogbackend.service.CategoryService;
import org.example.blogbackend.vo.CategoryVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor   //final,@NoNull可注入构造方法
/*类继承类，复用父类的代码实现*/
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    private final ArticleMapper articleMapper;

    @Override
    public CategoryVO create(CategoryCreateDTO dto) {
        if (existsByName(dto.getName(), null)) {
            throw new BusinessException(ResultCode.CONFLICT);
        }
        Category category = new Category();
        category.setName(dto.getName());
        save(category);   //添加
        return CategoryVO.from(getById(category.getId()));
    }

    @Override
    public CategoryVO update(Long id, CategoryUpdateDTO dto) {
        Category  category = getById(id);  //继承父类ServiceImpl方法getById,返回category类型，找不到id就返回null
        if(category == null){
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if(existsByName(dto.getName(),id)){
            throw new BusinessException(ResultCode.CONFLICT);
        }
        category.setName(dto.getName());
        updateById(category);  //更新
        return CategoryVO.from(getById(id));
    }

    @Override
    public void delete(Long id) {
        if(getById(id)==null){
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        Long refCount = articleMapper.selectCount(
                new LambdaQueryWrapper<Article>().eq(Article::getCategoryId, id)
        );
        if(refCount>0){
            throw new BusinessException(ResultCode.CONFLICT);
        }
        removeById(id);
    }

    @Override
    public CategoryVO getDetailById(Long id) {
        Category category = getById(id);
        if(category == null){
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return CategoryVO.from(category);
    }

    @Override
    public List<CategoryVO> getAll() {
        return list().stream().map(CategoryVO::from).toList();  //list(wrapper) ->
 //return list(new LambdaQueryWrapper<Category>.orderByAsc.map(CategoryVO.from).toList)
    }

    private boolean existsByName(String name, Long excludeId) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<Category>()
                .eq(Category::getName, name); //(操作name字段,传来的值)    SQL拼接：where name = ?
        if (excludeId != null) {
            wrapper.ne(Category::getId, excludeId); //排除 and id != ?
        }
        return count(wrapper) > 0;
    }
}
