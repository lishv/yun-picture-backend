package com.xiaoli.yunpicture.application.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoli.yunpicture.application.service.SpaceApplicationService;
import com.xiaoli.yunpicture.application.service.SpaceUserApplicationService;
import com.xiaoli.yunpicture.domain.space.service.SpaceDomainService;
import com.xiaoli.yunpicture.infrastructure.exception.BusinessException;
import com.xiaoli.yunpicture.infrastructure.exception.ErrorCode;
import com.xiaoli.yunpicture.infrastructure.exception.ThrowUtils;
//import com.xiaoli.yunpicturebackend.manager.sharding.DynamicShardingManager;
import com.xiaoli.yunpicture.interfaces.dto.space.SpaceAddRequest;
import com.xiaoli.yunpicture.interfaces.dto.space.SpaceLevel;
import com.xiaoli.yunpicture.interfaces.dto.space.SpaceQueryRequest;
import com.xiaoli.yunpicture.domain.space.entity.Space;
import com.xiaoli.yunpicture.domain.space.entity.SpaceUser;
import com.xiaoli.yunpicture.domain.user.entity.User;
import com.xiaoli.yunpicture.domain.space.valueobject.SpaceLevelEnum;
import com.xiaoli.yunpicture.domain.space.valueobject.SpaceRoleEnum;
import com.xiaoli.yunpicture.domain.space.valueobject.SpaceTypeEnum;
import com.xiaoli.yunpicture.interfaces.vo.space.SpaceVO;
import com.xiaoli.yunpicture.interfaces.vo.user.UserVO;

import com.xiaoli.yunpicture.infrastructure.mapper.SpaceMapper;

import com.xiaoli.yunpicture.application.service.UserApplicationService;
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
public class SpaceApplicationServiceImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceApplicationService {
    @Resource
    private UserApplicationService userApplicationService;
    @Resource
    private TransactionTemplate  transactionTemplate;
    @Resource
    private SpaceUserApplicationService spaceUserApplicationService;
    @Resource
    private SpaceDomainService spaceDomainService;
//    @Resource
//    @Lazy
//    private DynamicShardingManager dynamicShardingManager;

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
        // 校验空间实体类
        space.fillDefaultSpace();

        // 填充数据
        this.fillSpaceBySpaceLevel(space);
        // 数据校验
        space.validSpace(true);
        Long userId = loginUser.getId();
        space.setUserId(userId);
        //非管理员只能创建普通版空间
        if(SpaceLevelEnum.COMMON.getValue()!=space.getSpaceLevel()&&!loginUser.isAdmin()){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"无权限创建指定级别空间");
        }
        //4.控制统一用户只能创建一个私有空间,以及一个团队空间
        String lock = String.valueOf(userId).intern();
        synchronized (lock){
            Long newSpaceId = transactionTemplate.execute(status -> {
                //判断是否存在已创建的私有空间
                boolean exists = this.lambdaQuery()
                        .eq(Space::getUserId, userId)
                        .eq(Space::getSpaceType, space.getSpaceType())
                        .exists();
                //已有空间,则不允许创建
                if (exists) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "每个用户只能创建一个"+SpaceTypeEnum.getEnumByValue(space.getSpaceType()).getName());
                }
                //创建
                boolean result = this.save(space);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "保存空间失败");
                //创建成功后,如果是团队空间,关联新增团队成员记录
                if(SpaceTypeEnum.TEAM.getValue()==space.getSpaceType()){
                    SpaceUser spaceUser = new SpaceUser();
                    spaceUser.setSpaceId(space.getId());
                    spaceUser.setUserId(userId);
                    spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                    result= spaceUserApplicationService.save(spaceUser);
                    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建团队成员记录失败");


                }
                //TODO:为了方便部署,注释掉分表
                //创建分表(仅为旗舰团队空间生效创建-图片分表)
              //  dynamicShardingManager.createSpacePictureTable(space);
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
        return spaceDomainService.getQueryWrapper(spaceQueryRequest);
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
            User user = userApplicationService.getUserById(userId);
            UserVO userVO = userApplicationService.getUserVO(user);
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

        List<User> userList = userApplicationService.listByIds(userIdSet);

        Map<Long, List<User>> userIdUserListMap = userList.stream()
                .collect(Collectors.groupingBy(User::getId));

        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;

            if (userId != null && userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }

            spaceVO.setUser(userApplicationService.getUserVO(user));
        });

        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }



    /**
     * 根据空间级别填充空间容量和最大图片数量
     * @param space 空间实体类
     */
    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        spaceDomainService.fillSpaceBySpaceLevel(space);
    }

    @Override
    public void checkSpaceAuth(User loginUser, Space space) {
        //仅本人或者管理员可以编辑
       spaceDomainService.checkSpaceAuth(loginUser, space);
    }

    @Override
    public List<SpaceLevel> getSpaceLevelList() {
        return Arrays.stream(SpaceLevelEnum.values()) // 获取所有枚举
                .map(spaceLevelEnum -> new SpaceLevel(
                        spaceLevelEnum.getValue(),
                        spaceLevelEnum.getText(),
                        spaceLevelEnum.getMaxCount(),
                        spaceLevelEnum.getMaxSize()))
                .collect(Collectors.toList());
    }
}




