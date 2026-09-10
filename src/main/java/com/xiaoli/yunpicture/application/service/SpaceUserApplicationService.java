package com.xiaoli.yunpicture.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xiaoli.yunpicture.infrastructure.common.DeleteRequest;
import com.xiaoli.yunpicture.interfaces.dto.spaceuser.SpaceUserAddRequest;
import com.xiaoli.yunpicture.interfaces.dto.spaceuser.SpaceUserEditRequest;
import com.xiaoli.yunpicture.interfaces.dto.spaceuser.SpaceUserQueryRequest;
import com.xiaoli.yunpicture.domain.space.entity.SpaceUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoli.yunpicture.interfaces.vo.space.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author LiShuwen
* @description 针对表【space_user(空间用户关联)】的数据库操作Service
* @createDate 2026-09-05 19:48:34
*/
public interface SpaceUserApplicationService extends IService<SpaceUser> {
    /**
     * 添加空间用户关联
     * @param spaceUserAddRequest   空间用户关联请求参数
     * @return 空间用户关联 id
     */
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    /**
     * 验证空间用户关联
     * @param spaceUser   空间用户关联实体
     * @param add         是否添加
     */
    void validSpaceUser(SpaceUser spaceUser, boolean add);

    /**
     * 获取空间用户关联查询包装器
     * @param spaceUserQueryRequest   空间用户关联查询请求参数
     * @return 查询包装器
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);

    /**
     * 获取空间用户关联 vo
     * @param spaceUser   空间用户关联实体
     * @param request     http 请求
     * @return vo
     */
    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    /**
     * 获取空间用户关联 vo 列表
     * @param spaceUserList   空间用户关联实体列表
     * @return vo 列表
     */
    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);

    /**
     * 删除空间用户关联
     * @param deleteRequest   删除请求参数
     * @param request         http 请求
     */
    void deleteSpaceUser(DeleteRequest deleteRequest, HttpServletRequest request);

    /**
     * 编辑空间用户关联
     * @param spaceUser       空间用户关联实体
     * @param request         http 请求
     */
    void editSpaceUser(SpaceUserEditRequest spaceUserEditRequest, SpaceUser spaceUser,HttpServletRequest request);
}
