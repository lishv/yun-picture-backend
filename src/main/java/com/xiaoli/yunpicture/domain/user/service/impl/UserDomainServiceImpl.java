package com.xiaoli.yunpicture.domain.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoli.yunpicture.domain.repository.UserRepository;
import com.xiaoli.yunpicture.domain.user.constant.UserConstant;
import com.xiaoli.yunpicture.domain.user.entity.User;
import com.xiaoli.yunpicture.domain.user.service.UserDomainService;
import com.xiaoli.yunpicture.domain.user.valueobject.UserRoleEnum;
import com.xiaoli.yunpicture.infrastructure.exception.BusinessException;
import com.xiaoli.yunpicture.infrastructure.exception.ErrorCode;
import com.xiaoli.yunpicture.infrastructure.exception.ThrowUtils;
import com.xiaoli.yunpicture.interfaces.dto.user.UserLoginRequest;
import com.xiaoli.yunpicture.interfaces.dto.user.UserQueryRequest;
import com.xiaoli.yunpicture.interfaces.dto.user.UserRegisterRequest;
import com.xiaoli.yunpicture.interfaces.vo.user.UserVO;
import com.xiaoli.yunpicturebackend.manager.auth.StpKit;
import com.xiaoli.yunpicture.interfaces.vo.user.LoginUserVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
* @author LiShuwen
* @description 针对表【user(用户)】的数据库操作Service实现
* @createDate 2026-08-04 20:37:07
*/
@Slf4j
@Service
public class UserDomainServiceImpl implements UserDomainService {
    @Resource
    private UserRepository userRepository;
    
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

        //2.校验用户账号是否重复
        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<User>();
        lambdaQueryWrapper.eq(User::getUserAccount, userAccount);
        long count = userRepository.getBaseMapper().selectCount(lambdaQueryWrapper);
        ThrowUtils.throwIf(count > 0, ErrorCode.PARAMS_ERROR, "用户账号已存在");
        //等同于
        //if(count > 0){  throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号已存在");}
        //3.密码加密,不以明文存储
        String encryptPassword = getEncryptPassword(userPassword);
        //4.插入数据到数据库中
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("用户"+ UUID.randomUUID().toString());
        user.setUserRole(UserRoleEnum.User.getValue());
        boolean result = userRepository.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.SYSTEM_ERROR, "注册失败,数据库操作失败");
        //mybatis-plus默认返回插入的id,这里返回用户id
        return user.getId();
    }

/**
     * 用户登录
     * @param userLoginRequest   用户登录请求体
     * @return 登录用户信息
     */
    @Override
    public LoginUserVo userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request) {
        //1.解析密码
        //1.1校验密码
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();


        //1.2.对用户传递的密码进行加密
        String encryptPassword = getEncryptPassword(userPassword);
        //1.3判断数据库中是否存在该用户
        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<User>();
        lambdaQueryWrapper.eq(User::getUserAccount, userAccount).eq(User::getUserPassword, encryptPassword);
        User user = userRepository.getBaseMapper().selectOne(lambdaQueryWrapper);
        //1.4不存在抛异常
        if(user == null){
            log.info("user login fail,userAccount or userPassword can not match ");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号或密码错误");
        }
        //2.保存用户登陆状态到session中
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE,user);
        //3. 记录用户登录态到 Sa-token，便于空间鉴权时使用，注意保证该用户信息与 SpringSession 中的信息过期时间一致
        StpKit.SPACE.login(user.getId());
        StpKit.SPACE.getSession().set(UserConstant.USER_LOGIN_STATE,user);
        return this.getLoginUserVO(user);
    }

    /**
     * 将用户实体类转换为登录用户VO类
     * @param user 用户实体类
     * @return 登录用户VO类
     */
    @Override
    public LoginUserVo getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        return  BeanUtil.copyProperties(user, LoginUserVo.class);
    }

    /**
     * 将用户实体类转换为用户VO类
     * @param user 用户实体类
     * @return 用户VO类
     */
    @Override
    public UserVO getUserVO(User user) {
        if (BeanUtil.isEmpty(user)) {
            return null;
        }
        //1.转换用户VO类
        return BeanUtil.copyProperties(user, UserVO.class);
    }

    /**
     * 将用户列表转换为用户VO列表
     * @param userList 用户列表
     * @return 用户VO列表
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).toList();
    }


    /**
     * 获取加密后的密码
     * @param userPassword 明文密码
     * @return 加密后的密码
     */
    @Override
    public String getEncryptPassword(String userPassword){
        //加密混淆密码
        final String SALT="xiaoli";
        return DigestUtil.md5Hex(SALT+userPassword);
    }

    /**
     * 系统内部使用获取登录用户信息
     * @param request   HTTP请求对象
     * @return 登录用户信息
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        Object userObj= request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if(currentUser == null||currentUser.getId()==null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }
        Long userId = currentUser.getId();
        currentUser = userRepository.getBaseMapper().selectById(userId);
        if(currentUser == null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    /**
     * 用户退出登录
     * @param request   HTTP请求对象
     * @return 退出登录用户信息
     */
    @Override
    public boolean userLoginOut(HttpServletRequest request) {
        //1.判断用户是否登录
        Object userObj= request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if(currentUser == null||currentUser.getId()==null){
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户未登录");
        }
        //2.从session中移除用户登陆状态
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return true;
    }

    /**
     * 获取用户查询条件
     * @param userQueryRequest  用户查询请求体
     * @return 用户查询条件包装器
     */
    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
           throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        }
        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userAccount = userQueryRequest.getUserAccount();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();//排序字段
        String sortOrder = userQueryRequest.getSortOrder();//排序顺序
        QueryWrapper<User> queryWrapper= new QueryWrapper<User>();

//        Integer current = userQueryRequest.getCurrent();
//        Integer pageSize = userQueryRequest.getPageSize();
        queryWrapper
                .eq(ObjectUtil.isNotNull(id), "id", id)
                .like(StrUtil.isNotBlank(userName), "userName", userName)
                .like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount)
                .like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile)
                .eq(StrUtil.isNotBlank(userRole), "userRole", userRole)
                .orderBy(StrUtil.isNotBlank(sortField) && StrUtil.isNotBlank(sortOrder), "ascend".equals(sortOrder), sortField);

        return queryWrapper;
    }

    @Override
    public Boolean removeById(Long id) {
        return userRepository.removeById(id);
    }

    @Override
    public boolean updateById(User user) {
        return userRepository.updateById(user);
    }

    @Override
    public User getById(long id) {
        return userRepository.getById(id);
    }

    @Override
    public Page<User> page(Page<User> userPage, QueryWrapper<User> queryWrapper) {
        return userRepository.page(userPage, queryWrapper);
    }

    @Override
    public List<User> listByIds(Set<Long> userIdSet) {
        return userRepository.listByIds(userIdSet);
    }

    @Override
    public boolean saveUser(User userEntity) {
        return userRepository.save(userEntity);
    }


}




