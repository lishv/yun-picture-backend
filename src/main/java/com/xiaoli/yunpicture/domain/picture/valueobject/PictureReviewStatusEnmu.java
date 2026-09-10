package com.xiaoli.yunpicture.domain.picture.valueobject;

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;

/**
 * 图片审核状态枚举
 */
@Getter
public enum PictureReviewStatusEnmu {
    REVIEWING("待审核", 0),
    PASS("通过", 1),
    REJECT("拒绝", 2);

    private final String text;
    private final int value;
    /**
     * 构造方法
     * @param text 文本
     * @param value 值
     */
    PictureReviewStatusEnmu(String text, int value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据值获取枚举名
     * @param value 值
     * @return 枚举名
     */
    public static PictureReviewStatusEnmu getEnumByValue(Integer value) {
        if(ObjectUtil.isEmpty(value)){
            return null;
        }
        for (PictureReviewStatusEnmu pictureReviewStatusEnmu : PictureReviewStatusEnmu.values()) {
            if(pictureReviewStatusEnmu.getValue()==value){
                return pictureReviewStatusEnmu;
            }
        }
        return null;
    }
}
