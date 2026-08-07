package com.xiaoli.yunpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xiaoli.yunpicturebackend.model.dto.user.UserLoginRequest;
import com.xiaoli.yunpicturebackend.model.dto.user.UserQueryRequest;
import com.xiaoli.yunpicturebackend.model.dto.user.UserRegisterRequest;
import com.xiaoli.yunpicturebackend.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoli.yunpicturebackend.model.vo.LoginUserVo;
import com.xiaoli.yunpicturebackend.model.vo.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author LiShuwen
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2026-08-04 20:37:07
*/
public interface UserService extends IService<User> {
    /**
     * 用户注册
     * @param userRegisterRequest   用户注册请求体
     * @return 用户id
     */
    long userRegister(UserRegisterRequest userRegisterRequest);

    /**
     * 用户登录
     * @param userLoginRequest   用户登录请求体
     * @return 登录用户信息
     */
    LoginUserVo userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request);

    /**
     * 将用户实体类转换为登录用户VO类
     * @param user 用户实体类
     * @return 登录用户VO类
     */
    LoginUserVo getLoginUserVO(User user);

    /**
     * 获得脱敏后的用户信息
     * @param user 用户
     * @return  用户脱敏信息
     */
    UserVO getUserVO(User user);

    /**
     * 获得脱敏后的用户信息列表
     * @param userList 用户列表
     * @return 用户脱敏信息列表
     */
    List<UserVO> getUserVOList(List<User> userList);
    /**
     * 获取加密后的密码
     * @param userPassword 明文密码
     * @return 加密后的密码
     */
    String getEncryptPassword(String userPassword);

    /**
     * 系统内部使用获取登录用户信息
     * @param request   HTTP请求对象
     * @return 登录用户信息
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户退出登录
     * @param request   HTTP请求对象
     * @return 退出登录用户信息
     */
    boolean userLoginOut(HttpServletRequest request);

    /**
     * 获取用户查询条件
     * @param userQueryRequest  用户查询请求体
     * @return 用户查询条件包装器
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 判断用户是否为管理员
     * @param user 用户
     * @return 是否为管理员
     */
    boolean isAdmin(User user);
}
