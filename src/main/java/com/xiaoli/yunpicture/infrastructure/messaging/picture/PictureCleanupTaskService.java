package com.xiaoli.yunpicture.infrastructure.messaging.picture;

import com.xiaoli.yunpicture.domain.picture.entity.Picture;
import com.xiaoli.yunpicture.domain.picture.entity.PictureCleanupTask;
import com.xiaoli.yunpicture.domain.user.entity.User;
import com.xiaoli.yunpicture.infrastructure.exception.ErrorCode;
import com.xiaoli.yunpicture.infrastructure.exception.ThrowUtils;
import com.xiaoli.yunpicture.infrastructure.mapper.PictureCleanupTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 图片清理任务服务。
 */
@Service
@RequiredArgsConstructor
public class PictureCleanupTaskService {

    private final PictureCleanupTaskMapper pictureCleanupTaskMapper;

    /**
     * 创建待发送任务。
     *
     * MANDATORY 表示该方法必须在现有事务中执行，
     * 防止图片已经软删除但任务没有保存。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long createPendingTask(Picture oldPicture, User operator) {
        ThrowUtils.throwIf(oldPicture == null || oldPicture.getId() == null, ErrorCode.PARAMS_ERROR, "待清理图片不能为空");

        ThrowUtils.throwIf(operator == null || operator.getId() == null, ErrorCode.PARAMS_ERROR, "删除操作人不能为空");


        PictureCleanupTask pictureCleanupTask = new PictureCleanupTask();
        pictureCleanupTask.setPictureId(oldPicture.getId());
        pictureCleanupTask.setOperatorId(operator.getId());
        pictureCleanupTask.setStatus(PictureCleanupTask.STATUS_PENDING);

        int insertedCount = pictureCleanupTaskMapper.insert(pictureCleanupTask);

        ThrowUtils.throwIf(insertedCount != 1 || pictureCleanupTask.getId() == null, ErrorCode.OPERATION_ERROR, "创建图片清理任务失败");
        // 返回创建的任务 id, 用于后续处理
        return pictureCleanupTask.getId();
    }
}