package com.xiaoli.yunpicture.interfaces.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureUploadRequest implements Serializable {
    // 图片 id（用于修改）
    private Long id;
    /**
     * 空间 id
     */
    private Long spaceId;
    /**
     * 文件地址
     */
    private  String  fileUrl;
    /**
     * 图片名称的前缀
     */
    private String picName;
    /**
     * 图片来源哈希值
     */
    private  String sourceHash;
    /**
     * 图片分类
     */
    private String category;

    /**
     * 图片标签，JSON 字符串
     */
    private String tags;
    private static final long serialVersionUID = 1L;

}