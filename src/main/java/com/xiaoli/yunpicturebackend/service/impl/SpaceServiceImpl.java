package com.xiaoli.yunpicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoli.yunpicturebackend.exception.BusinessException;
import com.xiaoli.yunpicturebackend.exception.ErrorCode;
import com.xiaoli.yunpicturebackend.exception.ThrowUtils;
import com.xiaoli.yunpicturebackend.model.dto.space.SpaceAddRequest;
import com.xiaoli.yunpicturebackend.model.dto.space.SpaceQueryRequest;
import com.xiaoli.yunpicturebackend.model.entity.Space;
import com.xiaoli.yunpicturebackend.model.entity.User;
import com.xiaoli.yunpicturebackend.model.enums.SpaceLevelEnum;
import com.xiaoli.yunpicturebackend.model.vo.SpaceVO;
import com.xiaoli.yunpicturebackend.model.vo.UserVO;
import com.xiaoli.yunpicturebackend.service.SpaceService;
import com.xiaoli.yunpicturebackend.mapper.SpaceMapper;
import com.xiaoli.yunpicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
* @author LiShuwen
* @description 针对表【space(空间)】的数据库操作Service实现
* @createDate 2026-08-27 22:44:33
*/
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceService{
    @Resource
    private UserService userService;
    @Resource
    private TransactionTemplate  transactionTemplate;

    /**
     * 添加空间
     * @param spaceAddRequest 添加空间请求参数
     * @param loginUser 登录用户实体类
     * @return 空间id值
     */
    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        // 默认值
        if (StrUtil.isBlank(spaceAddRequest.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (spaceAddRequest.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        // 填充数据
        this.fillSpaceBySpaceLevel(space);
        // 数据校验
        this.validSpace(space, true);
        Long userId = loginUser.getId();
        space.setUserId(userId);
        //非管理员只能创建普通版空间
        if(SpaceLevelEnum.COMMON.getValue()!=space.getSpaceLevel()&&!userService.isAdmin(loginUser)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"无权限创建指定级别空间");
        }
        //4.控制统一用户只能创建一个私有空间
        String lock = String.valueOf(userId).intern();
        synchronized (lock){
            Long newSpaceId = transactionTemplate.execute(status -> {
                //判断是否存在已创建的私有空间
                boolean exists = this.lambdaQuery()
                        .eq(Space::getUserId, userId)
                        .exists();
                //已有私有空间,则不允许创建
                if (exists) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "每个用户只能创建一个私有空间");
                }
                //创建
                boolean result = this.save(space);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "保存空间失败");
                //返回新写入的id
                return space.getId();
            });
            return Optional.ofNullable(newSpaceId).orElse(-1L);
        }



    }

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

        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);

        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    /**
     * 获取空间详情
     * @param space 空间实体类
     * @param request HTTP请求对象
     * @return 空间详情
     */
    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        //对象转换成封装类
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        Long userId = space.getUserId();
        if(userId!=null&&userId>0){
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }

    /**
     * 获取空间分页列表
     * @param spacePage 分页对象
     * @param request HTTP请求对象
     * @return 空间分页列表
     */
    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();

        Page<SpaceVO> spaceVOPage = new Page<>(
                spacePage.getCurrent(),
                spacePage.getSize(),
                spacePage.getTotal()
        );

        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }

        // 将空间实体转换为空间 VO。
        List<SpaceVO> spaceVOList = spaceList.stream()
                .map(SpaceVO::objToVo)
                .collect(Collectors.toList());

        // 获取空间创建者 ID，批量查询用户，避免循环查询数据库。
        Set<Long> userIdSet = spaceList.stream()
                .map(Space::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (CollUtil.isEmpty(userIdSet)) {
            spaceVOPage.setRecords(spaceVOList);
            return spaceVOPage;
        }

        List<User> userList = userService.listByIds(userIdSet);

        Map<Long, List<User>> userIdUserListMap = userList.stream()
                .collect(Collectors.groupingBy(User::getId));

        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;

            if (userId != null && userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }

            spaceVO.setUser(userService.getUserVO(user));
        });

        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    /**
     * 校验空间数据是否符合要求
     * @param space 空间实体类
     * @param add 是否为创建时校验
     */
    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        if (add) {
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
        }
        //修改数据时校验
        if(StrUtil.isNotBlank(spaceName)&&spaceName.length()>30){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称长度不能超过30个字符");
        }
        if(spaceLevel!=null&&spaceLevelEnum==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
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
        if(!space.getUserId().equals(loginUser.getId())&&!userService.isAdmin(loginUser)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
    }
}




