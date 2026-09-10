package com.xiaoli.yunpicture.infrastructure.repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoli.yunpicture.domain.repository.UserRepository;
import com.xiaoli.yunpicture.infrastructure.mapper.UserMapper;
import com.xiaoli.yunpicture.domain.user.entity.User;
import org.springframework.stereotype.Service;

/**
 * 用户仓库实现类
 */
@Service
public class UserRepositoryImpl extends ServiceImpl<UserMapper, User> implements UserRepository {
}