package com.xiaoli.yunpicture.infrastructure.messaging.picture;

import com.xiaoli.yunpicture.domain.picture.entity.Picture;
import com.xiaoli.yunpicture.domain.picture.entity.PictureCleanupTask;
import com.xiaoli.yunpicture.domain.picture.service.PictureDomainService;
import com.xiaoli.yunpicture.infrastructure.manager.email.EmailManager;
import com.xiaoli.yunpicture.infrastructure.mapper.PictureCleanupTaskMapper;
import com.xiaoli.yunpicture.infrastructure.mapper.PictureMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 图片文件删除消息消费者。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PictureDeleteListener {

    private static final String CLEANUP_ALERT_COOLDOWN_KEY = "picture:cleanup:admin-email:cooldown";

    private final PictureCleanupTaskMapper pictureCleanupTaskMapper;

    private final PictureMapper pictureMapper;

    private final PictureDomainService pictureDomainService;

    private final EmailManager emailManager;

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${picture.cleanup.alert-email}")
    private String cleanupAlertEmail;

    /**
     * 第一次消费正常队列。
     */
    @RabbitListener(queues = MQPictureConstants.NORMAL_Picture_QUEUE_NAME)
    @Transactional(rollbackFor = Exception.class)
    public void consumeNormal(PictureDeleteMessage message) {
        consume(message, false);
    }

    /**
     * 30 分钟后的第二轮消费。
     *
     * 这里直接执行 COS 删除，不再返回正常队列。
     */
    @RabbitListener(queues = MQPictureConstants.DELAY_Picture_QUEUE_NAME)
    @Transactional(rollbackFor = Exception.class)
    public void consumeDelayed(PictureDeleteMessage message) {
        consume(message, true);
    }

    /**
     * 最终清理失败时通知超级管理员。
     */
    @RabbitListener(queues = MQPictureConstants.DLX_Picture_QUEUE_NAME)
    public void consumeDeadLetter(PictureDeleteMessage message) {
        Long taskId = message.getTaskId();
        Boolean shouldNotify = stringRedisTemplate.opsForValue()
                .setIfAbsent(CLEANUP_ALERT_COOLDOWN_KEY, "1", 30, TimeUnit.MINUTES);

        if (!Boolean.TRUE.equals(shouldNotify)) {
            log.info("30 分钟内已发送图片清理失败邮件，当前死信直接确认，taskId: {}", taskId);
            return;
        }

        try {
            emailManager.sendPictureCleanupFailureAlert(cleanupAlertEmail, taskId);
        } catch (RuntimeException exception) {
            stringRedisTemplate.delete(CLEANUP_ALERT_COOLDOWN_KEY);
            throw exception;
        }

        log.warn("图片清理失败告警邮件已发送，taskId: {}", taskId);
    }

    /**
     * 正常队列和延迟队列共用的删除逻辑。
     *
     * @param message 图片清理消息
     * @param delayedRetry 是否是延迟重试消息
     */
    private void consume(PictureDeleteMessage message, boolean delayedRetry) {
        if (message == null || message.getTaskId() == null) {throw new IllegalArgumentException("图片清理消息缺少 taskId");}
        Long taskId = message.getTaskId();

        /*
         * 使用 SELECT FOR UPDATE 锁住任务。
         * 如果 RabbitMQ 出现重复消息，同一个任务只能串行处理。
         */
        PictureCleanupTask task = pictureCleanupTaskMapper.selectByIdForUpdate(taskId);

        if (task == null) {throw new IllegalStateException("图片清理任务不存在，taskId：" + taskId);}

        Integer status = task.getStatus();

        if (status == null) {
            throw new IllegalStateException("图片清理任务状态为空，taskId：" + taskId);
        }

        /*
         * 已完成、最终失败或已人工处理的任务直接 ACK。
         * 这样重复消息不会再次删除 COS。
         */
        if (status == PictureCleanupTask.STATUS_COMPLETED
                || status == PictureCleanupTask.STATUS_FAILED
                || status == PictureCleanupTask.STATUS_RESOLVED) {

            log.debug("图片清理任务已结束，忽略重复消息，taskId: {}，status: {}", taskId, status);
            return;
        }

        /*
         * 正常队列收到等待延迟重试的重复消息时直接 ACK，
         * 避免跳过 30 分钟等待时间。
         */
        if (!delayedRetry && status == PictureCleanupTask.STATUS_WAITING_RETRY) {
            log.debug("图片清理任务正在等待延迟重试，忽略正常队列重复消息，taskId: {}", taskId);
            return;
        }

        /*
         * picture 已经软删除，不能使用普通 selectById。
         * 必须使用前面创建的不带 isDelete=0 条件的自定义查询。
         */
        Picture picture = pictureMapper.selectCleanupPictureIncludingDeleted(task.getPictureId());

        if (picture == null) {
            throw new IllegalStateException("待清理的图片记录不存在，pictureId：" + task.getPictureId());
        }

        /*
         * clearPictureFile(picture);执行 COS 删除。
         *
         * 不添加 @Async：
         * - 成功返回后，监听器才 ACK；
         * - 抛出异常时，Spring RabbitMQ 才能进行本地重试；
         * - 重试耗尽后，MessageRecoverer 才能转入延迟队列。
         */
        pictureDomainService.clearPictureFile(picture);

        task.setStatus(PictureCleanupTask.STATUS_COMPLETED);
        task.setLastError(null);
        task.setUpdateTime(new Date());

        int updated = pictureCleanupTaskMapper.updateById(task);

        if (updated != 1) {throw new IllegalStateException("更新图片清理任务完成状态失败，taskId：" + taskId);}

        log.info("图片文件清理完成，taskId: {}，pictureId: {}，delayedRetry: {}", taskId, task.getPictureId(), delayedRetry);
    }
}
