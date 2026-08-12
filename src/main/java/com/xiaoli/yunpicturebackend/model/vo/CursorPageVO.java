package com.xiaoli.yunpicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class CursorPageVO<T> implements Serializable {

    /**
     * 当前批次数据
     */
    private List<T> records;

    /**
     * 下一批游标的创建时间
     */
    private Date nextCursorCreateTime;

    /**
     * 下一批游标的 ID
     */
    private Long nextCursorId;

    /**
     * 是否还有更多数据
     */
    private Boolean hasMore;

    private static final long serialVersionUID = 1L;
}