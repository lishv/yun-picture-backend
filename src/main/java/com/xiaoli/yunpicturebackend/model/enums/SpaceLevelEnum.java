package com.xiaoli.yunpicturebackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

@Getter
public enum SpaceLevelEnum {
    COMMON("普通版", 0,100,100L*1024*1024),
    PROFESSIONAL("专业版", 1,1000,1000L*1024*1024),
    FLAGSHIP("旗舰版", 2,10000,10000L*1024*1024);

    private final String text;
    private final int value;
    private final long maxCount;
    private final long maxSize;

    /**
     * 构造函数
     * @param text 空间级别文本
     * @param value 空间级别值
     * @param maxSize 空间图片的最大总大小
     * @param maxCount 空间图片的最大数量
     */
    SpaceLevelEnum(String text, int value, long maxCount, long maxSize) {
        this.text = text;
        this.value = value;
        this.maxSize = maxSize;
        this.maxCount = maxCount;
    }
    /**
     * 根据value获取枚举
     * @param value 空间级别值
     */
    public static SpaceLevelEnum getEnumByValue(int value) {
        if(ObjUtil.isEmpty(value)){
            return null;
        }
        for (SpaceLevelEnum spaceLevelEnum : SpaceLevelEnum.values()) {
            if(spaceLevelEnum.getValue() == value){
                return spaceLevelEnum;
            }
        }
        return null;
    }
}
