package com.xiaoli.yunpicturebackend.model.dto.picture;

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
}