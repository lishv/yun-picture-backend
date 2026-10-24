package com.xiaoli.yunpicture.infrastructure.mapper;

import com.xiaoli.yunpicture.domain.picture.entity.Picture;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
* @author LiShuwen
* @description 针对表【picture(图片)】的数据库操作Mapper
* @createDate 2026-08-06 23:05:07
* @Entity com.xiaoli.yunpicture.domain.picture.entity.Picture
*/
public interface PictureMapper extends BaseMapper<Picture> {
    /**
     * 查询图片清理所需的信息。
     *
     * 这里故意使用自定义 SQL，不使用 selectById，
     * 因为 selectById 会自动过滤 isDelete = 1 的图片。
     */
    //只是允许 MQ 消费者读取软删除记录中的 URL。LIMIT 1限制查询结果最多只返回 1 条记录。
    @Select("""
            SELECT id,url,thumbnailUrl,name,userID AS userId, spaceId,isDelete FROM picture
            WHERE id = #{pictureId}
            LIMIT 1
            """)
    Picture selectCleanupPictureIncludingDeleted(@Param("pictureId") Long pictureId);
}




