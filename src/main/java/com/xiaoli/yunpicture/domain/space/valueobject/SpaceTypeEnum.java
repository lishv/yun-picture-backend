package com.xiaoli.yunpicture.domain.space.valueobject;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 空间类型枚举
 */
@Getter
public enum SpaceTypeEnum {
    PRIVATE("私有空间",0),
    TEAM("团队空间",1);
    private  final String name;
    private  final Integer value;

    SpaceTypeEnum(String name, Integer value) {
        this.name = name;
        this.value = value;
    }
    /**
     * 根据value获取枚举对象
     */
    public static SpaceTypeEnum getEnumByValue(Integer value){
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (SpaceTypeEnum spaceTypeEnum : SpaceTypeEnum.values()) {
            if (spaceTypeEnum.value == value) {
                return spaceTypeEnum;
            }
        }
        return null;
    }
}
