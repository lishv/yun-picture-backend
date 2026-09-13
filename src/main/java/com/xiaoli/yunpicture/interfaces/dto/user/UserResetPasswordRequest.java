package com.xiaoli.yunpicture.interfaces.dto.user;

import lombok.Data;
import java.io.Serializable;

/**
 * 管理员重置用户密码请求
 */
@Data
public class UserResetPasswordRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    private Long id;
}
