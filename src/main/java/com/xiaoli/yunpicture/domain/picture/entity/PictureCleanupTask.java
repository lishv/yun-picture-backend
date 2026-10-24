package com.xiaoli.yunpicture.domain.picture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图片文件清理任务
 */
@Data
@TableName("picture_cleanup_task")
public class PictureCleanupTask implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    /**
     * 待发送到正常 MQ
     */
    public static final int STATUS_PENDING = 0;

    /**
     * 已发送到正常 MQ，正在处理
     */
    public static final int STATUS_PROCESSING = 1;

    /**
     * 首轮消费失败，等待延迟重试
     */
    public static final int STATUS_WAITING_RETRY = 2;

    /**
     * COS 文件清理完成
     */
    public static final int STATUS_COMPLETED = 3;

    /**
     * 延迟重试后仍然失败，等待人工处理
     */
    public static final int STATUS_FAILED = 4;

    /**
     * 超级管理员已人工处理
     */
    public static final int STATUS_RESOLVED = 5;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联 picture.id
     */
    private Long pictureId;

    /**
     * 发起删除的用户 id
     */
    private Long operatorId;

    /**
     * 当前任务状态
     */
    private Integer status;

    /**
     * 最后一次失败原因
     */
    private String lastError;

    private Date createTime;

    private Date updateTime;
}