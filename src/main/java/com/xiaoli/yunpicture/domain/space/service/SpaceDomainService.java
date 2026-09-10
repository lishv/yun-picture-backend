package com.xiaoli.yunpicture.domain.space.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoli.yunpicture.domain.space.entity.Space;
import com.xiaoli.yunpicture.domain.user.entity.User;
import com.xiaoli.yunpicture.interfaces.dto.space.SpaceAddRequest;
import com.xiaoli.yunpicture.interfaces.dto.space.SpaceQueryRequest;
import com.xiaoli.yunpicture.interfaces.vo.space.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author LiShuwen
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2026-08-27 22:44:33
*/
public interface SpaceDomainService{

    /**
     * 获取查询条件对象
     * @param spaceQueryRequest 查询参数
     * @return 查询条件对象
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);



    /**
     * 根据空间级别填充空间容量和最大图片数量
     * @param space 空间实体类
     */
    void fillSpaceBySpaceLevel(Space space);

    /**
     * 检查空间分析权限
     * @param loginUser     登录用户实体类
     * @param space         空间实体类
     */
    void checkSpaceAuth(User loginUser, Space space);
/*

     * 使用复合游标查询图片列表
     *
     * @param spaceCursorQueryRequest 游标查询参数
     * @param request HTTP请求对象
     * @return 游标分页结果

CursorPageVO<SpaceVO> listSpaceVOByCursor(
        SpaceCursorQueryRequest spaceCursorQueryRequest,
        HttpServletRequest request
);
 */

}
