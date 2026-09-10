package com.xiaoli.yunpicture.interfaces.dto.space;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SpaceLevel {
    /**
     * 值
     */
    private int value;
    /**
     * 中文
     */
    private String text;
    /**
     * 最大图片数量
     */
    private long maxCount;
    /**
     * 最大图片容量
     */
    private long maxSize;
}