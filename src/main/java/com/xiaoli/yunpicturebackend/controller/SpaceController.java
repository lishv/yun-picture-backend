package com.xiaoli.yunpicturebackend.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoli.yunpicturebackend.annotation.AuthCheck;
import com.xiaoli.yunpicturebackend.common.BaseResponse;
import com.xiaoli.yunpicturebackend.common.DeleteRequest;
import com.xiaoli.yunpicturebackend.common.ResultUtils;
import com.xiaoli.yunpicturebackend.constant.UserConstant;
import com.xiaoli.yunpicturebackend.exception.BusinessException;
import com.xiaoli.yunpicturebackend.exception.ErrorCode;
import com.xiaoli.yunpicturebackend.exception.ThrowUtils;
import com.xiaoli.yunpicturebackend.model.dto.space.SpaceAddRequest;
import com.xiaoli.yunpicturebackend.model.dto.space.SpaceLevel;
import com.xiaoli.yunpicturebackend.model.dto.space.SpaceQueryRequest;
import com.xiaoli.yunpicturebackend.model.dto.space.SpaceUpdateRequest;
import com.xiaoli.yunpicturebackend.model.entity.Space;
import com.xiaoli.yunpicturebackend.model.entity.User;
import com.xiaoli.yunpicturebackend.model.enums.SpaceLevelEnum;
import com.xiaoli.yunpicturebackend.model.vo.CursorPageVO;
import com.xiaoli.yunpicturebackend.model.vo.SpaceVO;
import com.xiaoli.yunpicturebackend.service.SpaceService;
import com.xiaoli.yunpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/space")
public class SpaceController {
    @Resource
    private SpaceService spaceService;
    @Resource
    private UserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @PostMapping("/add")
    public BaseResponse<Long>addSpace(@RequestBody SpaceAddRequest  spaceAddRequest,HttpServletRequest request){
        ThrowUtils.throwIf(BeanUtil.isEmpty(spaceAddRequest),ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        long newId = spaceService.addSpace(spaceAddRequest,loginUser);
        return ResultUtils.success(newId);
    }
    /**
     * 删除空间
     * @param deleteRequest 删除请求参数
     * @param request HTTP请求对象
     * @return 删除结果
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean>deleteSpace(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if(BeanUtil.isEmpty(deleteRequest)||deleteRequest.getId()<=0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        Long id = deleteRequest.getId();
        //判断数据库是否存在
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(oldSpace),ErrorCode.NOT_FOUND_ERROR);
        //仅本人或者管理员可以删除,即如果这个空间不是本人上传的或者不是身份管理员,则不可以删除
        if(!oldSpace.getUserId().equals(loginUser.getId())&&!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        //软删除,删除操作数据库
        boolean result = spaceService.removeById(id);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(result);
    }

    /**
     * 更新空间信息(仅管理员可用)
     * @param spaceUpdateRequest 更新空间请求参数
     * @param request HTTP请求对象
     * @return 更新结果
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean>updateSpace(@RequestBody SpaceUpdateRequest spaceUpdateRequest, HttpServletRequest request) {
        if(BeanUtil.isEmpty(spaceUpdateRequest)||spaceUpdateRequest.getId()<=0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Space space = BeanUtil.copyProperties(spaceUpdateRequest, Space.class);
        //自动填充数据,容量和最大图片数量
        spaceService.fillSpaceBySpaceLevel(space);
        //数据校验,是否为创建操作,这里即更新
        spaceService.validSpace(space,false);
        //判断数据库是否存在
        long id = space.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(oldSpace),ErrorCode.NOT_FOUND_ERROR);
        //操作数据库
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据id获取空间信息(仅管理员可用)
     * @param id 空间id
     * @param request HTTP请求对象
     * @return 空间信息
     */
    @GetMapping("/id")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Space>getSpaceById(@RequestParam("id") Long id,HttpServletRequest request) {
        ThrowUtils.throwIf(id<=0,ErrorCode.PARAMS_ERROR);
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(space),ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(space);
    }

    /**
     * 根据id获取空间信息(用户,封装类)
     * @param id 空间id
     * @param request HTTP请求对象
     * @return 空间VO信息
     */
    @GetMapping("/get/vo")
    public BaseResponse<SpaceVO>getSpaceVOById(@RequestParam("id") Long id,HttpServletRequest request) {
        ThrowUtils.throwIf(id<=0,ErrorCode.PARAMS_ERROR);
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(space),ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(spaceService.getSpaceVO(space, request));
    }

    /**
     * 分页查询空间(仅管理员可用)
     * @param spaceQueryRequest 分页查询空间请求参数
     * @return 分页查询空间结果
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Space>>listSpaceByPage(@RequestBody SpaceQueryRequest spaceQueryRequest) {
        long pageSize = spaceQueryRequest.getPageSize();
        long current = spaceQueryRequest.getCurrent();
        //查询数据库
        Page<Space> spacePage = spaceService.page(new Page<>(current, pageSize), spaceService.getQueryWrapper(spaceQueryRequest));
        return  ResultUtils.success(spacePage);

    }

    /**
     * 分页查询空间(用户,封装类)
     * @param spaceQueryRequest 分页查询空间请求参数
     * @param request HTTP请求对象
     * @return 分页查询空间结果列表
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<SpaceVO>>listSpaceVOByPage(@RequestBody SpaceQueryRequest spaceQueryRequest,HttpServletRequest request) {
        long pageSize = spaceQueryRequest.getPageSize();
        long current = spaceQueryRequest.getCurrent();
        //限制爬虫
        ThrowUtils.throwIf(pageSize>20,ErrorCode.PARAMS_ERROR);
        //查询数据库
        Page<Space> spacePage = spaceService.page(new Page<>(current, pageSize), spaceService.getQueryWrapper(spaceQueryRequest));
        //返回空间封装类列表
        return  ResultUtils.success(spaceService.getSpaceVOPage(spacePage, request));
    }


    /**
     * 编辑空间信息(仅本人/管理员可用)
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceUpdateRequest spaceUpdateRequest, HttpServletRequest request) {
        if(BeanUtil.isEmpty(spaceUpdateRequest)|| spaceUpdateRequest.getId()<=0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Space space = BeanUtil.copyProperties(spaceUpdateRequest, Space.class);
        //自动填充数据
        spaceService.fillSpaceBySpaceLevel(space);
        //设置编辑时间
        space.setEditTime(new Date());
        //数据校验
        spaceService.validSpace(space,false);
        User loginUser = userService.getLoginUser(request);
        //判断空间是否存在
        Long id = space.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(oldSpace),ErrorCode.NOT_FOUND_ERROR);

        //仅本人/管理员可以编辑
        if(!oldSpace.getUserId().equals(loginUser.getId())&&!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        //操作数据库
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 获取空间等级列表
     * @return 空间等级列表
     */
    @GetMapping("/list/level")
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {
        List<SpaceLevel> spaceLevelList = Arrays.stream(SpaceLevelEnum.values()) // 获取所有枚举
                .map(spaceLevelEnum -> new SpaceLevel(
                        spaceLevelEnum.getValue(),
                        spaceLevelEnum.getText(),
                        spaceLevelEnum.getMaxCount(),
                        spaceLevelEnum.getMaxSize()))
                .collect(Collectors.toList());
        return ResultUtils.success(spaceLevelList);
    }



}
