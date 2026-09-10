package com.xiaoli.yunpicture.interfaces.dto.space.analyze;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间排名分析(仅管理员)
 */
@Data
public class SpaceRankAnalyzeRequest implements Serializable {

    /**
     * 排名前 N 的空间
     */
    private Integer topN = 10;

    private static final long serialVersionUID = 1L;
}