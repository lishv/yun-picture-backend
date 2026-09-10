package com.xiaoli.yunpicture.domain.space.service.impl;

import cn.hutool.core.util.ObjUtil;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import com.xiaoli.yunpicture.application.service.SpaceApplicationService;

import com.xiaoli.yunpicture.domain.space.entity.SpaceUser;
import com.xiaoli.yunpicture.domain.space.service.SpaceUserDomainService;

import com.xiaoli.yunpicture.interfaces.dto.spaceuser.SpaceUserQueryRequest;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
* @author LiShuwen
* @description 针对表【space_user(空间用户关联)】的数据库操作Service实现
* @createDate 2026-09-05 19:48:34
*/
@Service
public class SpaceUserDomainServiceImpl implements SpaceUserDomainService {
    @Lazy
    private SpaceApplicationService spaceApplicationService;


    /**
     * 根据空间查询条件构建查询包装器
     * @param spaceUserQueryRequest 查询参数
     * @return 查询包装器
     */
    @Override
    public QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest) {
        QueryWrapper<SpaceUser> queryWrapper = new QueryWrapper<>();
        if (spaceUserQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = spaceUserQueryRequest.getId();
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        String spaceRole = spaceUserQueryRequest.getSpaceRole();
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceRole), "spaceRole", spaceRole);
        return queryWrapper;
    }



}




