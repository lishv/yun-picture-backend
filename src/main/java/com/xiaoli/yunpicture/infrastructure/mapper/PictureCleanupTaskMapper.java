package com.xiaoli.yunpicture.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoli.yunpicture.domain.picture.entity.PictureCleanupTask;
import com.xiaoli.yunpicture.interfaces.vo.picture.PictureCleanupFailureVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 图片文件清理任务 Mapper
 */
public interface PictureCleanupTaskMapper
        extends BaseMapper<PictureCleanupTask> {

    /**
     * 加行锁查询任务，防止重复 MQ 消息同时删除同一个文件。
     *
     * 必须在事务中调用，FOR UPDATE 才会生效。
     */
    @Select("""
            SELECT id,pictureId,operatorId,status,lastError,createTime,updateTime
            FROM picture_cleanup_task
            WHERE id = #{taskId}
            FOR UPDATE
            """)
    PictureCleanupTask selectByIdForUpdate(
            @Param("taskId") Long taskId
    );
    /**
     * 分页查询最终失败的图片清理任务。
     *
     * 这里直接关联软删除后的 picture，不附加 isDelete = 0。
     */
    @Select("""
            SELECT t.id AS id,
                   t.pictureId AS pictureId,
                   p.name AS pictureName,
                   p.userID AS pictureUserId,
                   p.spaceId AS spaceId,
                   t.operatorId AS operatorId,
                   u.userName AS operatorName,
                   p.url AS url,
                   p.thumbnailUrl AS thumbnailUrl,
                   t.lastError AS lastError,
                   t.createTime AS createTime,
                   t.updateTime AS failedTime
            FROM picture_cleanup_task t
            LEFT JOIN picture p
                   ON p.id = t.pictureId
            LEFT JOIN `user` u
                   ON u.id = t.operatorId
            WHERE t.status = #{failedStatus}
            ORDER BY t.updateTime DESC, t.id DESC
            """)
    Page<PictureCleanupFailureVO> selectFailedPage(
            Page<PictureCleanupFailureVO> page,
            @Param("failedStatus") Integer failedStatus
    );
}