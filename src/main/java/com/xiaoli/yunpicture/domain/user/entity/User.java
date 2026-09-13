package com.xiaoli.yunpicture.domain.user.entity;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.util.Date;

import com.xiaoli.yunpicture.domain.user.valueobject.UserRoleEnum;
import com.xiaoli.yunpicture.infrastructure.exception.BusinessException;
import com.xiaoli.yunpicture.infrastructure.exception.ErrorCode;
import com.xiaoli.yunpicture.interfaces.dto.user.UserLoginRequest;
import lombok.Data;

/**
 * 用户
 * @TableName user
 */
@TableName(value ="user")
@Data
public class User  implements Serializable {
    @TableField(exist = false)
    private static final long serialVersionUID = -3253146341856880870L;
    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    private String userRole;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
    /**
     * 邮箱
     */
    private String email;
    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    /**
     * 校验用户注册参数
     * @param userAccount 用户账号
     * @param userPassword 用户密码
     * @param checkPassword 确认密码
     */
    public static  void  validUserRegister( String userAccount, String userPassword,String checkPassword) {
        //1.校验参数
        if (StrUtil.isBlank(userAccount)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号不能为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号长度不能小于6位");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码长度不能小于8位");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入密码不一致");
        }
    }

    /**
     * 校验用户登录参数
     * @param userAccount 用户账号
     * @param userPassword 用户密码
     */
    public static  void  validUserLogin(String userAccount,String userPassword) {
        //1.解析密码
        //1.1校验密码

        if(StrUtil.isBlank(userAccount)||StrUtil.isBlank(userPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码不能为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号错误");
        }
        if (userPassword.length() < 8 ) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码错误");
        }

    }
    /**
     * 判断用户是否为管理员
     * @return 是否为管理员
     */
    public boolean isAdmin() {
        return userRole.equals(UserRoleEnum.Admin.getValue());
    }

}