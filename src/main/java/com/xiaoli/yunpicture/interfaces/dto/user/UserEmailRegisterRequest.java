package com.xiaoli.yunpicture.interfaces.dto.user;

import lombok.Data;
import java.io.Serializable;

/**
 * 邮箱验证码注册请求
 */
@Data
public class UserEmailRegisterRequest implements Serializable {

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 6位验证码
     */
    private String code;

    /**
     * 密码
     */
    private String userPassword;

    /**
     * 校验密码
     */
    private String checkPassword;

    private static final long serialVersionUID = 1L;
}
