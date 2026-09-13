package com.xiaoli.yunpicture.application.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoli.yunpicture.domain.user.constant.UserConstant;
import com.xiaoli.yunpicture.domain.user.service.UserDomainService;
import com.xiaoli.yunpicture.infrastructure.common.DeleteRequest;
import com.xiaoli.yunpicture.infrastructure.exception.BusinessException;
import com.xiaoli.yunpicture.infrastructure.exception.ErrorCode;
import com.xiaoli.yunpicture.infrastructure.exception.ThrowUtils;
import com.xiaoli.yunpicture.interfaces.dto.user.*;
import com.xiaoli.yunpicture.domain.user.entity.User;
import com.xiaoli.yunpicture.interfaces.vo.user.LoginUserVo;
import com.xiaoli.yunpicture.interfaces.vo.user.UserVO;
import com.xiaoli.yunpicture.application.service.UserApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;

/**
* @author LiShuwen
* @description 针对表【user(用户)】的数据库操作Service实现
* @createDate 2026-08-04 20:37:07
*/
@Slf4j
@Service
public class UserApplicationServiceImpl
    implements UserApplicationService {
    @Resource
    private UserDomainService userDomainService;
    /**
     * 用户注册
     * @param userRegisterRequest   用户注册请求体
     * @return 用户id
     */
    @Override
    public long userRegister(UserRegisterRequest userRegisterRequest) {
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        //1.校验
        User.validUserRegister(userAccount, userPassword, checkPassword);
        //2.执行
        return  userDomainService.userRegister(userRegisterRequest);
    }

/**
     * 用户登录
     * @param userLoginRequest   用户登录请求体
     * @return 登录用户信息
     */
    @Override
    public LoginUserVo userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request) {
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        User.validUserLogin(userAccount, userPassword);
       return userDomainService.userLogin(userLoginRequest,request);
    }

    /**
     * 将用户实体类转换为登录用户VO类
     * @param user 用户实体类
     * @return 登录用户VO类
     */
    @Override
    public LoginUserVo getLoginUserVO(User user) {
        return  userDomainService.getLoginUserVO(user);
    }

    /**
     * 将用户实体类转换为用户VO类
     * @param user 用户实体类
     * @return 用户VO类
     */
    @Override
    public UserVO getUserVO(User user) {
      return userDomainService.getUserVO(user);
    }

    /**
     * 将用户列表转换为用户VO列表
     * @param userList 用户列表
     * @return 用户VO列表
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        return userDomainService.getUserVOList(userList);
    }


    /**
     * 获取加密后的密码
     * @param userPassword 明文密码
     * @return 加密后的密码
     */
    @Override
    public String getEncryptPassword(String userPassword){

        return userDomainService.getEncryptPassword(userPassword);
    }

    /**
     * 系统内部使用获取登录用户信息
     * @param request   HTTP请求对象
     * @return 登录用户信息
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {

        return userDomainService.getLoginUser(request);
    }

    /**
     * 用户退出登录
     * @param request   HTTP请求对象
     * @return 退出登录用户信息
     */
    @Override
    public boolean userLoginOut(HttpServletRequest request) {
       return userDomainService.userLoginOut(request);
    }

    /**
     * 获取用户查询条件
     * @param userQueryRequest  用户查询请求体
     * @return 用户查询条件包装器
     */
    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        return   userDomainService.getQueryWrapper(userQueryRequest);
    }

    @Override
    public boolean changePassword(UserChangePasswordRequest userChangePasswordRequest, User loginUser) {
       return userDomainService.changePassword(userChangePasswordRequest, loginUser);

    }

    @Override
    public boolean resetPassword(Long userId) {
        return userDomainService.resetPassword(userId);
    }


    @Override
    public User getUserById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userDomainService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return user;
    }

    @Override
    public UserVO getUserVOById(long id) {
        return userDomainService.getUserVO(getUserById(id));
    }

    @Override
    public boolean deleteUser(DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return userDomainService.removeById(deleteRequest.getId());
    }

    @Override
    public void updateUser(User user) {
        boolean result = userDomainService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    @Override
    public Page<UserVO> listUserVOByPage(UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = userQueryRequest.getCurrent();
        long size = userQueryRequest.getPageSize();
        Page<User> userPage = userDomainService.page(new Page<>(current, size),
                userDomainService.getQueryWrapper(userQueryRequest));
        Page<UserVO> userVOPage = new Page<>(current, size, userPage.getTotal());
        List<UserVO> userVO = userDomainService.getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVO);
        return userVOPage;
    }

    @Override
    public List<User> listByIds(Set<Long> userIdSet) {
        return userDomainService.listByIds(userIdSet);
    }

    @Override
    public long saveUser(User userEntity) {
        String encryptPassword = userDomainService.getEncryptPassword(UserConstant.DEFAULT_PASSWORD);
        userEntity.setUserPassword(encryptPassword);
        // 插入数据库
        boolean result = userDomainService.saveUser(userEntity);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return userEntity.getId();
    }

    /**
     * 用户自己修改用户信息
     * @param userEditRequest
     * @param request
     */
    @Override
    public void editUser(UserEditRequest userEditRequest, HttpServletRequest request) {
       userDomainService.editUser(userEditRequest,request);
    }

}




