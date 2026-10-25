package com.xiaoli.yunpicture.interfaces.vo.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图片文件清理失败信息。
 */
@Data
public class PictureCleanupFailureVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long pictureId;

    private String pictureName;

    /**
     * 图片上传者。
     */
    private Long pictureUserId;

    private Long spaceId;

    /**
     * 执行删除操作的用户。
     */
    private Long operatorId;

    private String operatorName;

    private String url;

    private String thumbnailUrl;

    private String lastError;

    /**
     * 删除任务创建时间。
     */
    private Date createTime;

    /**
     * 最终失败时间，使用任务最后更新时间。
     */
    private Date failedTime;
}