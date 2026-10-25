package com.xiaoli.yunpicture.infrastructure.messaging.picture;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xiaoli.yunpicture.domain.picture.entity.PictureCleanupTask;
import com.xiaoli.yunpicture.infrastructure.mapper.PictureCleanupTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 图片清理任务发布器。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PictureCleanupPublisher {

    private final PictureCleanupTaskMapper PictureCleanupTaskMapper;

    private final RabbitMqHelper rabbitMqHelper;

    /**
     * 在独立线程中发布消息，HTTP 请求线程不等待 RabbitMQ。
     */
    @Async
    public void publishAsync(Long taskId) {
        if (taskId == null) {return;}
        // 从数据库查询任务
        PictureCleanupTask task = PictureCleanupTaskMapper.selectById(taskId);

        // 只发送待发送任务，避免已经完成或正在重试的任务被重复发布
        if (task == null ||!Integer.valueOf(PictureCleanupTask.STATUS_PENDING).equals(task.getStatus())) {return;}

        PictureDeleteMessage pictureDeleteMessage = new PictureDeleteMessage();
        pictureDeleteMessage.setTaskId(taskId);// 设置删除信息的任务 ID

        try {
            // 发送消息到 RabbitMQ
            rabbitMqHelper.sendPersistentMessageWithConfirm(
                    MQPictureConstants.NORMAL_Picture_EXCHANGE_NAME,
                    MQPictureConstants.NORMAL_Picture_KEY,
                    pictureDeleteMessage
            );

            // 只允许待发送状态变为处理中。
            int updated = PictureCleanupTaskMapper.update(
                    null,
                    new LambdaUpdateWrapper<PictureCleanupTask>()
                            .eq(PictureCleanupTask::getId, taskId)
                            .eq(PictureCleanupTask::getStatus,PictureCleanupTask.STATUS_PENDING)
                            .set(PictureCleanupTask::getStatus, PictureCleanupTask.STATUS_PROCESSING)
                            .set(PictureCleanupTask::getLastError, null)
                            .set(PictureCleanupTask::getUpdateTime, new Date())
            );

            if (updated == 0) {
                log.debug("图片清理任务状态已被其他线程更新，taskId: {}", taskId);
            }
        } catch (Exception exception) {
            // 保持 STATUS_PENDING，后续由待发送任务补偿扫描重新发布
            log.error("图片清理任务发送 MQ 失败，保留待发送状态，taskId: {}", taskId, exception);
        }
    }
}