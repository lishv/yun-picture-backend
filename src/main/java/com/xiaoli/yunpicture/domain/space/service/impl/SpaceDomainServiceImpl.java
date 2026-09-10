package com.xiaoli.yunpicture.domain.space.service.impl;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import com.xiaoli.yunpicture.domain.space.entity.Space;

import com.xiaoli.yunpicture.domain.space.repository.SpaceRepository;
import com.xiaoli.yunpicture.domain.space.service.SpaceDomainService;
import com.xiaoli.yunpicture.domain.space.valueobject.SpaceLevelEnum;

import com.xiaoli.yunpicture.domain.user.entity.User;
import com.xiaoli.yunpicture.infrastructure.exception.BusinessException;
import com.xiaoli.yunpicture.infrastructure.exception.ErrorCode;

import com.xiaoli.yunpicture.interfaces.dto.space.SpaceQueryRequest;

import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.*;

/**
* @author LiShuwen
* @description 针对表【space(空间)】的数据库操作Service实现
* @createDate 2026-08-27 22:44:33
*/
@Service
public class SpaceDomainServiceImpl  implements SpaceDomainService {

//    @Resource
//    @Lazy
//    private DynamicShardingManager dynamicShardingManager;
@Resource
private SpaceRepository spaceRepository;

    /**
     * 获取查询条件对象
     * @param spaceQueryRequest 查询参数
     * @return 查询条件对象
     */
    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();
        Integer spaceType = spaceQueryRequest.getSpaceType();
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceType), "spaceType", spaceType);

        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }



    /**
     * 根据空间级别填充空间容量和最大图片数量
     * @param space 空间实体类
     */
    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if(spaceLevelEnum!=null){
            long maxSize = spaceLevelEnum.getMaxSize();
            if(space.getMaxSize()==null){
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
            if(space.getMaxCount()==null){
                space.setMaxCount(maxCount);
            }
        }
    }

    @Override
    public void checkSpaceAuth(User loginUser, Space space) {
        //仅本人或者管理员可以编辑
        if(!space.getUserId().equals(loginUser.getId())&&!loginUser.isAdmin()){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
    }
}




