package com.xiaoli.yunpicturebackend.model.dto.picture;

import io.swagger.models.auth.In;
import lombok.Data;

import java.io.Serializable;

@Data
public class PictureUploadByBatchRequest implements Serializable {
    /**
     * 搜索词
     */
    private String searchText;
    /**
     * 抓取数量,默认10张,建议小于30张
     */
    private Integer count=10;
    private static final long serialVersionUID = 1L;
    /**
     * 图片名称的前缀
     */
    private String namePrefix;
    /**
     * 跳过的搜索结果数量
     * offset=0：从第1条开始
     * offset=10：跳过前10条，从第11条开始
     */
    private Integer offset=0;


    /**
     * 分类
     */
    private String category;

    /**
     * 标签 (JSON 数组)
     */
    private String tags;

}