package com.xiaoli.yunpicture.interfaces.dto.user;

import lombok.Data;
import java.io.Serializable;

/**
 * 邮箱验证码重置密码请求
 */
@Data
public class UserEmailResetPasswordRequest implements Serializable {

    /**
     * 邮箱
     */
    private String email;

    /**
     * 6位验证码
     */
    private String code;

    /**
     * 新密码
     */
    private String newPassword;

    /**
     * 确认新密码
     */
    private String checkPassword;

    private static final long serialVersionUID = 1L;
}
