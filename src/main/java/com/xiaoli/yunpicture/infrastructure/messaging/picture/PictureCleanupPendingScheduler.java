package com.xiaoli.yunpicture.infrastructure.messaging.picture;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaoli.yunpicture.domain.picture.entity.PictureCleanupTask;
import com.xiaoli.yunpicture.infrastructure.mapper.PictureCleanupTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 补偿尚未成功发送到 RabbitMQ 的图片清理任务。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PictureCleanupPendingScheduler {

    private final PictureCleanupTaskMapper taskMapper;

    private final PictureCleanupPublisher publisher;

    /**
     * 应用启动 30 秒后开始执行；
     * 此后每次执行结束 60 秒后再次执行。
     */
    @Scheduled(
            initialDelay = 30_000L,
            fixedDelay = 60_000L
    )
    public void resendPendingTasks() {
        /*
         * 每次只取 50 条，防止一次加载太多任务。
         * 只查询任务 id，因为发布器会重新检查任务状态。
         */
        List<PictureCleanupTask> pendingTasks =
                taskMapper.selectList(new LambdaQueryWrapper<PictureCleanupTask>()
                                .select(PictureCleanupTask::getId)
                                .eq(PictureCleanupTask::getStatus, PictureCleanupTask.STATUS_PENDING)
                                .orderByAsc(PictureCleanupTask::getUpdateTime)
                                .last("LIMIT 50")
                );

        if (pendingTasks == null || pendingTasks.isEmpty()) {return;}

        log.info("发现 {} 个待发送的图片清理任务，开始补偿发送", pendingTasks.size());

        for (PictureCleanupTask task : pendingTasks) {
            try {
                /*
                 * publishAsync 使用 Spring 默认异步执行器。
                 * 定时扫描线程本身不会等待 RabbitMQ Confirm。
                 */
                publisher.publishAsync(task.getId());
            } catch (RuntimeException exception) {
                /*
                 * 某一个任务提交异步执行失败时，
                 * 不影响其他待发送任务。
                 */
                log.error("提交图片清理补偿任务失败，taskId: {}", task.getId(), exception);
            }
        }
    }
}