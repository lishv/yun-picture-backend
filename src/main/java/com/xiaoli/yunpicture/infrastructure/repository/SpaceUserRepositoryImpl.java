package com.xiaoli.yunpicture.infrastructure.repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.xiaoli.yunpicture.domain.space.entity.SpaceUser;
import com.xiaoli.yunpicture.domain.space.repository.SpaceUserRepository;
import com.xiaoli.yunpicture.infrastructure.mapper.SpaceUserMapper;
import org.springframework.stereotype.Service;

/**
 * 空间用户仓储实现
 */
@Service
public class SpaceUserRepositoryImpl extends ServiceImpl<SpaceUserMapper, SpaceUser> implements SpaceUserRepository {
}