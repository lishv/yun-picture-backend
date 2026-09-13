package com.xiaoli.yunpicture.interfaces.dto.user;

import lombok.Data;
import java.io.Serializable;

/**
 * 用户编辑个人信息请求
 */
@Data
public class UserEditRequest implements Serializable {
    private static final long serialVersionUID = 1L;

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
}
