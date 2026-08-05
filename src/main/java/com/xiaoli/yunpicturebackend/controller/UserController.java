package com.xiaoli.yunpicturebackend.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoli.yunpicturebackend.annotation.AuthCheck;
import com.xiaoli.yunpicturebackend.common.BaseResponse;
import com.xiaoli.yunpicturebackend.common.DeleteRequest;
import com.xiaoli.yunpicturebackend.common.ResultUtils;
import com.xiaoli.yunpicturebackend.constant.UserConstant;
import com.xiaoli.yunpicturebackend.exception.ErrorCode;
import com.xiaoli.yunpicturebackend.exception.ThrowUtils;
import com.xiaoli.yunpicturebackend.model.dto.user.*;
import com.xiaoli.yunpicturebackend.model.entity.User;
import com.xiaoli.yunpicturebackend.model.vo.LoginUserVo;
import com.xiaoli.yunpicturebackend.model.vo.UserVO;
import com.xiaoli.yunpicturebackend.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private UserService userService;

    /**
     * 用户注册
     * @param userRegisterRequest 用户注册请求参数
     * @return 用户ID
     */

    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(userRegisterRequest==null, ErrorCode.PARAMS_ERROR);
        long userId = userService.userRegister(userRegisterRequest);
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
        User user = BeanUtil.copyProperties(userAddRequest, User.class);
        //设置默认密码
        String encryptPassword = userService.getEncryptPassword(UserConstant.DEFAULT_PASSWORD);
        user.setUserPassword(encryptPassword);
        //插入数据库
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
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
        return ResultUtils.success(userService.userLogin(userLoginRequest, request));
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
        User user = userService.getById(id);
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
        BaseResponse<User> response = getUserById(id);//获取用户实体类
        User user = response.getData();
        return ResultUtils.success(userService.getUserVO(user));
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
        ThrowUtils.throwIf(deleteRequest.getId()==null||deleteRequest.getId()<=0, ErrorCode.NOT_FOUND_ERROR);
        boolean result = userService.removeById(deleteRequest.getId());
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
        User user = BeanUtil.copyProperties(userUpdateRequest, User.class);
        //插入数据库
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
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
        long current = userQueryRequest.getCurrent();
        long pageSize = userQueryRequest.getPageSize();
        Page<User> userPage = userService.page(new Page<>(current, pageSize), userService.getQueryWrapper(userQueryRequest));
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());
        //创建一个新的、泛型为 UserVO 的分页对象，保留原始分页信息（当前页、每页大小、总记录数）
        Page<UserVO> userVOPage = new Page<>(current, pageSize, userPage.getTotal());
        //将用户VO列表设置到新的分页对象中
        userVOPage.setRecords(userVOList);
        //返回新的分页对象
        return ResultUtils.success(userVOPage);

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
        return ResultUtils.success(userService.getLoginUserVO(userService.getLoginUser(request)));
    }

    /**
     * 用户退出登录
     * @param request HTTP请求对象
     * @return 退出登录结果
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogOut(HttpServletRequest request) {
      if(request==null){
          return ResultUtils.success(false);
      }
        return ResultUtils.success(userService.userLoginOut(request));
    }



}
