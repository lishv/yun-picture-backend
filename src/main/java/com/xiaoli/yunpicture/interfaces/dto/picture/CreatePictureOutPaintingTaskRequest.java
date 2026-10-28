package com.xiaoli.yunpicture.interfaces.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class CreatePictureOutPaintingTaskRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * AI 绘图提示词
     */
    private String prompt;

    private static final long serialVersionUID = 1L;
}
