package com.xiaoli.yunpicture.infrastructure.messaging.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 图片文件删除消息
 *
 * MQ 只传任务 id，图片信息从数据库读取，
 * 避免在任务表和消息中重复保存图片字段。
 */
@Data
public class PictureDeleteMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long taskId;
}