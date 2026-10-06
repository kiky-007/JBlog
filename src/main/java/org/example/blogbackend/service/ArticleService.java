package org.example.blogbackend.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.example.blogbackend.common.result.PageResult;
import org.example.blogbackend.dto.ArticleCreateDTO;
import org.example.blogbackend.dto.ArticleUpdateDTO;
import org.example.blogbackend.entity.Article;
import org.example.blogbackend.vo.ArticleListVO;
import org.example.blogbackend.vo.ArticleVO;

public interface ArticleService extends IService<Article> {

    ArticleVO create(ArticleCreateDTO dto);

    ArticleVO getDetailById(Long id);

    //(第几页,每页几条,按分类筛选（可选）,按状态筛选（可选）);
    PageResult<ArticleListVO> pageList(Long pageNo, Long pageSize, Long categoryId, Integer status);

    ArticleVO update(Long id, ArticleUpdateDTO dto);

    void delete(Long id);
}
