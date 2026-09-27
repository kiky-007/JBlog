package org.example.blogbackend.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.example.blogbackend.dto.CategoryCreateDTO;
import org.example.blogbackend.dto.CategoryUpdateDTO;
import org.example.blogbackend.entity.Category;
import org.example.blogbackend.vo.CategoryVO;

import java.util.List;

/*接口继承接口，扩展接口的能力范围*/
public interface CategoryService extends IService<Category> {

    public CategoryVO create(CategoryCreateDTO dto);

    public CategoryVO update(Long id,CategoryUpdateDTO dto);  //根据id来更新

    public void delete(Long id);

    public CategoryVO getDetailById(Long id);     //根据id查询

    public List<CategoryVO> getAll();      //查询全部
}
