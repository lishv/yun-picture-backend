package com.xiaoli.yunpicture.domain.space.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoli.yunpicture.domain.space.entity.SpaceUser;
import com.xiaoli.yunpicture.interfaces.dto.spaceuser.SpaceUserAddRequest;
import com.xiaoli.yunpicture.interfaces.dto.spaceuser.SpaceUserQueryRequest;
import com.xiaoli.yunpicture.interfaces.vo.space.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author LiShuwen
* @description 针对表【space_user(空间用户关联)】的数据库操作Service
* @createDate 2026-09-05 19:48:34
*/
public interface SpaceUserDomainService  {

    /**
     * 获取空间用户关联查询包装器
     * @param spaceUserQueryRequest   空间用户关联查询请求参数
     * @return 查询包装器
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);
}
