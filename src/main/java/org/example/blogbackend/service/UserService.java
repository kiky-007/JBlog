package org.example.blogbackend.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.example.blogbackend.dto.UserCreateDTO;
import org.example.blogbackend.entity.User;
import org.example.blogbackend.vo.UserVO;

public interface UserService extends IService<User> {

    UserVO create(UserCreateDTO dto);

    UserVO getDetailById(Long id);

    void delete(Long id);
}
