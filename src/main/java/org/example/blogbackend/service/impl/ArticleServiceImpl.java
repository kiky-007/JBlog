package org.example.blogbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.example.blogbackend.common.exception.BusinessException;
import org.example.blogbackend.common.result.PageResult;
import org.example.blogbackend.common.result.ResultCode;
import org.example.blogbackend.dto.ArticleCreateDTO;
import org.example.blogbackend.dto.ArticleUpdateDTO;
import org.example.blogbackend.entity.Article;
import org.example.blogbackend.entity.ArticleTag;
import org.example.blogbackend.entity.Category;
import org.example.blogbackend.entity.Tag;
import org.example.blogbackend.entity.User;
import org.example.blogbackend.mapper.ArticleMapper;
import org.example.blogbackend.mapper.ArticleTagMapper;
import org.example.blogbackend.mapper.CategoryMapper;
import org.example.blogbackend.mapper.TagMapper;
import org.example.blogbackend.mapper.UserMapper;
import org.example.blogbackend.service.ArticleService;
import org.example.blogbackend.vo.ArticleListVO;
import org.example.blogbackend.vo.ArticleVO;
import org.example.blogbackend.vo.TagVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;

    @Override
    @Transactional
    public ArticleVO create(ArticleCreateDTO dto) {
        validateRefs(dto.getCategoryId(), dto.getAuthorId(), dto.getTagIds());

        Article article = new Article();
        article.setTitle(dto.getTitle());
        article.setContent(dto.getContent());
        article.setSummary(dto.getSummary());
        article.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        article.setCategoryId(dto.getCategoryId());
        article.setAuthorId(dto.getAuthorId());
        save(article);

        saveArticleTags(article.getId(), dto.getTagIds());
        return toDetailVO(getById(article.getId()));
    }

    @Override
    public ArticleVO getDetailById(Long id) {
        Article article = getById(id);
        if (article == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return toDetailVO(article);
    }

    @Override
    public PageResult<ArticleListVO> pageList(Long pageNo, Long pageSize, Long categoryId, Integer status) {
        Page<Article> pageParam = new Page<>(pageNo, pageSize);  //Page是MyBatis-Plus的分页对象
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()   //查询条件
                .eq(categoryId != null, Article::getCategoryId, categoryId)
                .eq(status != null, Article::getStatus, status)
                .orderByDesc(Article::getCreatedAt);
        page(pageParam, wrapper);   //在 MybatisPlusConfig 里注册的 PaginationInnerInterceptor 会拦截这条 SQL。IService 提供的分页方法

        List<Article> records = pageParam.getRecords();  //records文章列表
        Map<Long, String> categoryNames = categoryNames(records);
        Map<Long, String> authorNames = authorNames(records);

        List<ArticleListVO> vos = records.stream()
                .map(a -> ArticleListVO.from(a, categoryNames.get(a.getCategoryId()), authorNames.get(a.getAuthorId())))
                .toList();
        return new PageResult<>(pageParam.getCurrent(), pageParam.getSize(), pageParam.getTotal(), vos);
    }

    @Override
    @Transactional
    public ArticleVO update(Long id, ArticleUpdateDTO dto) {
        Article article = getById(id);
        if (article == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        validateRefs(dto.getCategoryId(), null, dto.getTagIds());

        article.setTitle(dto.getTitle());
        article.setContent(dto.getContent());
        article.setSummary(dto.getSummary());
        article.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        article.setCategoryId(dto.getCategoryId());
        updateById(article);

        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, id));
        saveArticleTags(id, dto.getTagIds());
        return toDetailVO(getById(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (getById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, id));
        removeById(id);
    }

    /*校验分类、作者、标签是否存在*/
    private void validateRefs(Long categoryId, Long authorId, List<Long> tagIds) {
        if (categoryId != null && categoryMapper.selectById(categoryId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (authorId != null && userMapper.selectById(authorId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (tagIds != null && !tagIds.isEmpty()) {
            List<Long> distinct = tagIds.stream().distinct().toList(); //去重
            Long count = tagMapper.selectCount(new LambdaQueryWrapper<Tag>().in(Tag::getId, distinct)); //数据库标签数量
            if (count != distinct.size()) {
                throw new BusinessException(ResultCode.NOT_FOUND);
            }
        }
    }

    /*保存文章与标签关联关系的方法
      把某篇文章关联的使用标签，逐条写入中间表article_tag*/
    private void saveArticleTags(Long articleId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;   //文章可以没有标签
        }
        for (Long tagId : tagIds.stream().distinct().toList()) {
            ArticleTag articleTag = new ArticleTag();
            articleTag.setArticleId(articleId);
            articleTag.setTagId(tagId);
            articleTagMapper.insert(articleTag);  //执行插入
        }
    }

    /*根据文章实体，查出所有关联数据，组装成一个完整的 ArticleVO 返回给前端*/
    private ArticleVO toDetailVO(Article article) {
        Category category = categoryMapper.selectById(article.getCategoryId());
        User author = userMapper.selectById(article.getAuthorId());
        //查标签列表 [1,2,3]
        List<Long> tagIds = articleTagMapper.selectList(
                        new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, article.getId()))
                .stream().map(ArticleTag::getTagId).toList();
        //查标签详情
        List<TagVO> tags = tagIds.isEmpty()
                ? List.of() //返回空列表
                : tagMapper.selectBatchIds(tagIds).stream().map(TagVO::from).toList();

        return ArticleVO.from(article,
                category == null ? null : category.getName(),
                author == null ? null : author.getUsername(),
                tags);
    }

    /*批量查询文章分类名称*/
    private Map<Long, String> categoryNames(List<Article> articles) {  //Map(分类Id,分类名称)
        List<Long> ids = articles.stream().map(Article::getCategoryId).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap(); //返回空Map
        }
        return categoryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName)); //collet是流的终结操作，作用是把流里的元素"收集"成另一种数据结构(可以是List,Set,Map,String)
    }

    /*批量查询文章作者名称*/
    private Map<Long, String> authorNames(List<Article> articles) {
        List<Long> ids = articles.stream().map(Article::getAuthorId).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
    }
}
