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
     * 空间 ID
     * 为空时查询公共图片
     */
    private Long spaceId;
    /**
     * 是否指查询spaceId为null的图片,因此查询公共图片只需设置为true
     */
    private  Boolean nullSpaceId ;


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
    /**
     * 审核状态: 0-待审核; 1-通过; 2-拒绝
     */
    private Integer reviewStatus;

    /**
     * 审核信息
     */
    private String reviewMessage;

    /**
     * 审核人 ID
     */
    private Long reviewerId;

    /**
     * 审核时间
     */
    private Date reviewTime;

    private static final long serialVersionUID = 1L;
}