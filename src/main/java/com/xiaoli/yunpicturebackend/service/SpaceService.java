package com.xiaoli.yunpicturebackend.service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoli.yunpicturebackend.model.dto.space.SpaceAddRequest;
import com.xiaoli.yunpicturebackend.model.dto.space.SpaceQueryRequest;
import com.xiaoli.yunpicturebackend.model.entity.Space;
import com.xiaoli.yunpicturebackend.model.entity.User;
import com.xiaoli.yunpicturebackend.model.vo.CursorPageVO;
import com.xiaoli.yunpicturebackend.model.vo.SpaceVO;
import org.springframework.web.bind.annotation.RequestBody;

import javax.servlet.http.HttpServletRequest;

/**
* @author LiShuwen
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2026-08-27 22:44:33
*/
public interface SpaceService extends IService<Space> {
    /**
     * 添加空间
     * @param spaceAddRequest 添加空间请求参数
     * @param loginUser 登录用户实体类
     * @return 空间id值
     */
    long  addSpace(SpaceAddRequest  spaceAddRequest, User loginUser);
    /**
     * 获取查询条件对象
     * @param spaceQueryRequest 查询参数
     * @return 查询条件对象
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 获取空间包装类(单条)
     * @param space 空间实体类
     * @param request HTTP请求对象
     * @return 空间VO类
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    /**
     * 获取图片包装类(多条)
     * @param spacePage 分页对象
     * @param request HTTP请求对象
     * @return 分页对象,包含空间VO类列表
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    /**
     * 校验空间数据是否符合要求
     * @param space 空间实体类
     */
    void validSpace(Space space ,boolean add);

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
