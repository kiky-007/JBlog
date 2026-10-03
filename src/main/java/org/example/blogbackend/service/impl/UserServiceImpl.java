package org.example.blogbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.example.blogbackend.common.exception.BusinessException;
import org.example.blogbackend.common.result.ResultCode;
import org.example.blogbackend.dto.UserCreateDTO;
import org.example.blogbackend.entity.Article;
import org.example.blogbackend.entity.User;
import org.example.blogbackend.mapper.ArticleMapper;
import org.example.blogbackend.mapper.UserMapper;
import org.example.blogbackend.service.UserService;
import org.example.blogbackend.vo.UserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final ArticleMapper articleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserVO create(UserCreateDTO dto) {
        if (existsByUsername(dto.getUsername())) {
            throw new BusinessException(ResultCode.CONFLICT);
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        save(user);
        return UserVO.from(getById(user.getId()));
    }

    @Override
    public UserVO getDetailById(Long id) {
        User user = getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return UserVO.from(user);
    }

    @Override
    public void delete(Long id) {
        if (getById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        Long refCount = articleMapper.selectCount(
                new LambdaQueryWrapper<Article>().eq(Article::getAuthorId, id));
        if (refCount > 0) {
            throw new BusinessException(ResultCode.CONFLICT);
        }
        removeById(id);
    }

    private boolean existsByUsername(String username) {
        return count(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0;
    }
}
