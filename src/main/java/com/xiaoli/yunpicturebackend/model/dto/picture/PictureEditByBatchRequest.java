package com.xiaoli.yunpicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PictureEditByBatchRequest implements Serializable {

    /**FF
     * 图片 id 列表
     */
    private List<Long> pictureIdList;

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签
     */
    private List<String> tags;
    /**
     * 图片名称规则
     */
    private  String nameRule;

    private static final long serialVersionUID = 1L;
}