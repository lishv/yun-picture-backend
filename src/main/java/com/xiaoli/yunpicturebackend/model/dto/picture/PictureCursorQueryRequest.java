package com.xiaoli.yunpicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class PictureCursorQueryRequest implements Serializable {

    /**
     * 搜索词，同时搜索名称和简介
     */
    private String searchText;

    /**
     * 图片分类
     */
    private String category;

    /**
     * 图片标签
     */
    private List<String> tags;

    /**
     * 每次加载数量
     */
    private Integer pageSize = 20;

    /**
     * 上一批最后一张图片的创建时间
     */
    private Date cursorCreateTime;

    /**
     * 上一批最后一张图片的 ID
     */
    private Long cursorId;

    private static final long serialVersionUID = 1L;
}