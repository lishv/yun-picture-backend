package com.xiaoli.yunpicture.domain.user.valueobject;

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;

/**
 * 用户角色枚举
 */
@Getter
public enum UserRoleEnum {
    User("用户", "user"),
    Admin("管理员", "admin");

    private final String text;
    private final String value;
    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据值获取枚举
     * @param value 值
     * @return 枚举
     */
    public static UserRoleEnum getEnumByValue(String value) {
        if(ObjectUtil.isEmpty(value)){
            return null;
        }
        for (UserRoleEnum roleEnum : UserRoleEnum.values()) {
            if(roleEnum.getValue().equals(value)){
                return roleEnum;
            }
        }
        return null;
    }
}
