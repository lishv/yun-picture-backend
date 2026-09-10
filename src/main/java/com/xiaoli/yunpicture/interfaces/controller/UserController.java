package com.xiaoli.yunpicture.interfaces.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoli.yunpicture.infrastructure.annotation.AuthCheck;
import com.xiaoli.yunpicture.infrastructure.common.BaseResponse;
import com.xiaoli.yunpicture.infrastructure.common.DeleteRequest;
import com.xiaoli.yunpicture.infrastructure.common.ResultUtils;
import com.xiaoli.yunpicture.interfaces.assembler.UserAssembler;
import com.xiaoli.yunpicture.interfaces.dto.user.*;
import com.xiaoli.yunpicture.domain.user.constant.UserConstant;
import com.xiaoli.yunpicture.infrastructure.exception.ErrorCode;
import com.xiaoli.yunpicture.infrastructure.exception.ThrowUtils;
import com.xiaoli.yunpicture.domain.user.entity.User;
import com.xiaoli.yunpicture.interfaces.vo.user.LoginUserVo;
import com.xiaoli.yunpicture.interfaces.vo.user.UserVO;
import com.xiaoli.yunpicture.application.service.UserApplicationService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private UserApplicationService userApplicationService;

    /**
     * 用户注册
     * @param userRegisterRequest 用户注册请求参数
     * @return 用户ID
     */

    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(userRegisterRequest==null, ErrorCode.PARAMS_ERROR);
        long userId = userApplicationService.userRegister(userRegisterRequest);
        return ResultUtils.success(userId);
    }
    /**
     * 用户添加
     * @param userAddRequest 用户添加请求参数
     * @return 用户ID
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/add")
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwIf(userAddRequest==null, ErrorCode.PARAMS_ERROR);
        User user = UserAssembler.toUserEntity(userAddRequest);
        return ResultUtils.success(userApplicationService.saveUser(user));
    }

    /**
     * 用户登录
     * @param userLoginRequest 用户登录请求参数
     * @param request HTTP请求对象
     * @return 登录用户信息
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVo> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userLoginRequest==null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(userApplicationService.userLogin(userLoginRequest, request));
    }

    /**
     * 根据ID获取用户信息(仅管理员)
     * @param id 用户ID
     * @return 用户信息
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/get")
    public BaseResponse<User>getUserById(@RequestParam Long id){
        ThrowUtils.throwIf(id==null||id<=0, ErrorCode.PARAMS_ERROR);
        User user = userApplicationService.getUserById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(user), ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }
    /**
     * 根据ID获取用户信息(用户)
     * @param id 用户ID
     * @return 用户信息
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO>getUserVoById(@RequestParam Long id){
      return ResultUtils.success(userApplicationService.getUserVOById(id));
    }

    /**
     * 删除用户(仅管理员)
     * @param deleteRequest 删除请求参数
     * @return 删除结果
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest){
        ThrowUtils.throwIf(BeanUtil.isEmpty(deleteRequest), ErrorCode.PARAMS_ERROR);

        boolean result = userApplicationService.deleteUser(deleteRequest);
        return ResultUtils.success(result);
    }

    /**
     * 更新用户信息(仅管理员)
     * @param userUpdateRequest 更新请求参数
     * @return 更新结果
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/update")
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        ThrowUtils.throwIf(BeanUtil.isEmpty(userUpdateRequest)||userUpdateRequest.getId()==null||userUpdateRequest.getId()<=0, ErrorCode.PARAMS_ERROR);
        User user = UserAssembler.toUserEntity(userUpdateRequest);
        //插入数据库
        userApplicationService.updateUser(user);
        return ResultUtils.success(true);
    }

    /**
     * 分页查询用户VO列表(仅管理员)
     * @param userQueryRequest 查询请求参数
     * @return 用户VO分页列表
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest){
        ThrowUtils.throwIf(BeanUtil.isEmpty(userQueryRequest), ErrorCode.PARAMS_ERROR);
       return ResultUtils.success(userApplicationService.listUserVOByPage(userQueryRequest));


    }

    /**
     * 获取登录用户信息
     * @param request HTTP请求对象
     * @return 登录用户信息
     */
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVo> getLoginUser(HttpServletRequest request) {
        //1.获取登录用户信息
        //2.将用户实体类转换为登录用户VO类并返回
        return ResultUtils.success(userApplicationService.getLoginUserVO(userApplicationService.getLoginUser(request)));
    }

    /**
     * 用户退出登录
     * @param request HTTP请求对象
     * @return 退出登录结果
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
      if(request==null){
          return ResultUtils.success(false);
      }
        return ResultUtils.success(userApplicationService.userLoginOut(request));
    }



}
