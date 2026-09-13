package com.xiaoli.yunpicture.interfaces.dto.user;

import lombok.Data;
import java.io.Serializable;

/**
 * 发送邮箱验证码请求
 */
@Data
public class UserSendEmailCodeRequest implements Serializable {

    /**
     * 邮箱地址
     */
    private String email;

    /**
     * 业务场景：register（注册） / reset_password（重置/找回密码）
     */
    private String biz;

    private static final long serialVersionUID = 1L;
}
