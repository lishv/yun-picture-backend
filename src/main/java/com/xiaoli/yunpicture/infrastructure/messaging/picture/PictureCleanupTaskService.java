package com.xiaoli.yunpicture.infrastructure.messaging.picture;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoli.yunpicture.domain.picture.entity.Picture;
import com.xiaoli.yunpicture.domain.picture.entity.PictureCleanupTask;
import com.xiaoli.yunpicture.domain.user.entity.User;
import com.xiaoli.yunpicture.infrastructure.exception.ErrorCode;
import com.xiaoli.yunpicture.infrastructure.exception.ThrowUtils;
import com.xiaoli.yunpicture.infrastructure.mapper.PictureCleanupTaskMapper;
import com.xiaoli.yunpicture.interfaces.vo.picture.PictureCleanupFailureVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;

/**
 * 图片清理任务服务。
 */
@Service
@RequiredArgsConstructor
public class PictureCleanupTaskService {

    private final PictureCleanupTaskMapper pictureCleanupTaskMapper;

    /**
     * 在图片删除事务中创建待发送任务。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long createPendingTask(Picture oldPicture, User operator) {
        ThrowUtils.throwIf(oldPicture == null || oldPicture.getId() == null, ErrorCode.PARAMS_ERROR, "待清理图片不能为空");

        ThrowUtils.throwIf(operator == null || operator.getId() == null, ErrorCode.PARAMS_ERROR, "删除操作人不能为空"
        );

        PictureCleanupTask task = new PictureCleanupTask();
        task.setPictureId(oldPicture.getId());
        task.setOperatorId(operator.getId());
        task.setStatus(PictureCleanupTask.STATUS_PENDING);

        int inserted = pictureCleanupTaskMapper.insert(task);

        ThrowUtils.throwIf(inserted != 1 || task.getId() == null, ErrorCode.OPERATION_ERROR, "创建图片清理任务失败"
        );

        return task.getId();
    }

    /**
     * 正常队列消费耗尽后，标记为等待延迟重试。
     *
     * 使用独立事务，保证状态单独提交。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markWaitingRetry(Long taskId, Throwable cause) {
        updateStatus(taskId, PictureCleanupTask.STATUS_WAITING_RETRY, cause, PictureCleanupTask.STATUS_PENDING, PictureCleanupTask.STATUS_PROCESSING);
    }

    /**
     * 延迟队列消费仍然失败，标记为最终失败。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long taskId, Throwable cause) {
        updateStatus(taskId, PictureCleanupTask.STATUS_FAILED, cause,
                PictureCleanupTask.STATUS_PENDING,
                PictureCleanupTask.STATUS_PROCESSING,
                PictureCleanupTask.STATUS_WAITING_RETRY
        );
    }

    private void updateStatus(Long taskId, int targetStatus, Throwable cause, Integer... allowedStatuses) {
        if (taskId == null) {throw new IllegalArgumentException("图片清理任务 id 不能为空");}

        int updated = pictureCleanupTaskMapper.update(
                null,
                new LambdaUpdateWrapper<PictureCleanupTask>()
                        .eq(PictureCleanupTask::getId, taskId)
                        .in(PictureCleanupTask::getStatus, Arrays.asList(allowedStatuses))
                        .set( PictureCleanupTask::getStatus, targetStatus)
                        .set(PictureCleanupTask::getLastError, resolveError(cause))
                        .set(PictureCleanupTask::getUpdateTime, new Date())
        );

        if (updated == 1) {
            return;
        }

        /*
         * 可能是重复消息造成状态已被其他消费者更新。
         */
        PictureCleanupTask current = pictureCleanupTaskMapper.selectById(taskId);

        if (current != null && current.getStatus() != null && (current.getStatus()
                == targetStatus
                        || current.getStatus()
                        == PictureCleanupTask.STATUS_COMPLETED
                        || current.getStatus()
                        == PictureCleanupTask.STATUS_FAILED
                        || current.getStatus()
                        == PictureCleanupTask.STATUS_RESOLVED
        )) {return;}

        throw new IllegalStateException("更新图片清理任务状态失败，taskId：" + taskId);
    }

    /**
     * 提取最底层异常并限制在数据库字段长度以内。
     * 从异常链中提取最底层（根因）异常的信息，格式化为字符串，并截断到 1000 字符以内，以便存入数据库。
     */
    private String resolveError(Throwable cause) {
        if (cause == null) {return "未知错误";}

        Throwable root = cause;

        while (root.getCause() != null
                && root.getCause() != root) {
            root = root.getCause();
        }

        String message = root.getMessage();

        if (message == null || message.isBlank()) {
            message = root.getClass().getName();
        } else {
            message = root.getClass().getSimpleName()
                    + ": " + message;
        }

        if (message.length() > 1000) {
            return message.substring(0, 1000);
        }

        return message;
    }

    /**
     * 分页查询最终失败任务。
     */
    public Page<PictureCleanupFailureVO> listFailedTasks(
            int current,
            int pageSize
    ) {
        ThrowUtils.throwIf(
                current <= 0,
                ErrorCode.PARAMS_ERROR,
                "当前页必须大于 0"
        );

        ThrowUtils.throwIf(
                pageSize <= 0 || pageSize > 100,
                ErrorCode.PARAMS_ERROR,
                "每页数量必须在 1 到 100 之间"
        );

        Page<PictureCleanupFailureVO> page = new Page<>(current, pageSize);

        return pictureCleanupTaskMapper.selectFailedPage(
                page,
                PictureCleanupTask.STATUS_FAILED
        );
    }

    /**
     * 超级管理员确认 COS 文件已经人工处理。
     *
     * 本方法只关闭失败记录，不会再次删除 COS，
     * 也不会消费最终死信队列中的消息。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean resolveFailedTask(Long taskId) {
        ThrowUtils.throwIf(
                taskId == null || taskId <= 0,
                ErrorCode.PARAMS_ERROR,
                "任务 id 不正确"
        );

        int updated = pictureCleanupTaskMapper.update(
                null,
                new LambdaUpdateWrapper<PictureCleanupTask>()
                        .eq(PictureCleanupTask::getId, taskId)
                        .eq(
                                PictureCleanupTask::getStatus,
                                PictureCleanupTask.STATUS_FAILED
                        )
                        .set(
                                PictureCleanupTask::getStatus,
                                PictureCleanupTask.STATUS_RESOLVED
                        )
                        .set(
                                PictureCleanupTask::getUpdateTime,
                                new Date()
                        )
        );

        ThrowUtils.throwIf(
                updated != 1,
                ErrorCode.OPERATION_ERROR,
                "任务不存在、已经处理或当前不是最终失败状态"
        );

        return true;
    }
}