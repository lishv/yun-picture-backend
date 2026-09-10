package com.xiaoli.yunpicture.infrastructure.repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.xiaoli.yunpicture.domain.space.entity.Space;
import com.xiaoli.yunpicture.domain.space.repository.SpaceRepository;
import com.xiaoli.yunpicture.infrastructure.mapper.SpaceMapper;
import org.springframework.stereotype.Service;

/**
 * 空间仓储实现
 */
@Service
public class SpaceRepositoryImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceRepository {
}