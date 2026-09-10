package com.xiaoli.yunpicture.interfaces.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户登录请求体
 */
@Data
public class UserLoginRequest implements Serializable {
    private static final long serialVersionUID = -4623004035840950780L;
    /**
     * 账号
     */
     private String userAccount;
     /**
     *密码
     */
     private String userPassword;

}
