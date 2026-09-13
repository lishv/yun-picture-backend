package com.xiaoli.yunpicture.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoli.yunpicture.infrastructure.common.DeleteRequest;
import com.xiaoli.yunpicture.interfaces.dto.user.*;
import com.xiaoli.yunpicture.domain.user.entity.User;
import com.xiaoli.yunpicture.interfaces.vo.user.LoginUserVo;
import com.xiaoli.yunpicture.interfaces.vo.user.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;

/**
* @author LiShuwen
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2026-08-04 20:37:07
*/
public interface UserApplicationService {
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
    /**
     * 用户修改密码
     *
     * @param userChangePasswordRequest 修改密码请求
     * @param loginUser 登录用户
     * @return 是否修改成功
     */
    boolean changePassword(UserChangePasswordRequest userChangePasswordRequest, User loginUser);

    /**
     * 重置用户密码为默认密码 (仅管理员)
     *
     * @param userId 用户 ID
     * @return 是否重置成功
     */
    boolean resetPassword(Long userId);

    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

    User getUserById(long id);

    UserVO getUserVOById(long id);

    boolean deleteUser(DeleteRequest deleteRequest);

    void updateUser(User user);

    Page<UserVO> listUserVOByPage(UserQueryRequest userQueryRequest);

    List<User> listByIds(Set<Long> userIdSet);

    long saveUser(User userEntity);

    /**
     * 用户自己修改用户信息
     * @param userEditRequest
     * @param request
     */
    void editUser(UserEditRequest userEditRequest, HttpServletRequest request);
}
