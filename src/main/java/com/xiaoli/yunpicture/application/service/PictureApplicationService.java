package com.xiaoli.yunpicture.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoli.yunpicture.infrastructure.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.xiaoli.yunpicture.infrastructure.common.BaseResponse;
import com.xiaoli.yunpicture.interfaces.dto.picture.*;
import com.xiaoli.yunpicture.domain.picture.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoli.yunpicture.domain.user.entity.User;
import com.xiaoli.yunpicture.interfaces.vo.picture.CursorPageVO;
import com.xiaoli.yunpicture.interfaces.vo.picture.PictureVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author LiShuwen
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2026-08-06 23:05:07
*/
public interface PictureApplicationService extends IService<Picture> {
    /**
     * 上传图片
     * @param inputSource 图片文件或url
     * @param pictureUploadRequest 图片上传请求参数
     * @param loginUser 登录用户对象
     * @return 图片VO类
     */
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) ;

    /**
     * 获取查询条件对象
     * @param pictureQueryRequest 查询参数
     * @return 查询条件对象
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    /**
     * 获取图片包装类(单条)
     * @param picture 图片实体类
     * @param request HTTP请求对象
     * @return 图片VO类
     */
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    /**
     * 获取图片包装类(多条)
     * @param picturePage 分页对象
     * @param request HTTP请求对象
     * @return 分页对象,包含图片VO类列表
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 校验图片数据是否符合要求
     * @param picture 图片实体类
     */
    void validatePicture(Picture picture);

    /**
     * 使用复合游标查询图片列表
     *
     * @param pictureCursorQueryRequest 游标查询参数
     * @param request HTTP 请求
     * @return 游标分页结果
     */
    CursorPageVO<PictureVO> listPictureVOByCursor(
            PictureCursorQueryRequest pictureCursorQueryRequest,
            HttpServletRequest request
    );

    /**
     * 图片审核
     * @param pictureReviewRequest
     * @param loginUser
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    /**
     * 填充审核参数
     * @param picture 图片实体类
     * @param loginUser 登录用户实体类
     */
    void fillReviewParams(Picture picture, User loginUser);

    /**
     * 批量上传图片
     * @param pictureUploadByBatchRequest 图片上传批量请求参数
     * @param loginUser 登录用户对象
     * @return 上传成功数量
     */
    Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser);

    /**
     * 删除图片文件
     * @param picture 图片实体类
     */
    void clearPictureFile(Picture picture);
    /**
     * 校验空间的图片权限
     * @param picture 图片实体类
     */
    void checkPictureAuth(User loginUser, Picture picture);

    /**
     * 删除图片
     * @param pictureId 图片ID
     * @param loginUser 登录用户对象

     */

    void deletePicture(long pictureId, User loginUser);

    /**
     * 编辑图片
     * @param picture 图片实体类
     * @param loginUser 登录用户对象
     * @return 编辑成功
     */
    BaseResponse<Boolean> editPicture(Picture picture, User loginUser);

    /**
     * 根据颜色查询图片
     * @param spaceId 空间ID
     * @param picColor 图片颜色
     * @param loginUser 登录用户对象
     * @return 图片VO类列表
     */
    List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser);

    /**
     * 批量编辑图片
     * @param pictureEditByBatchRequest 图片编辑批量请求参数
     * @param loginUser 登录用户对象
     */
    void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser);

    /**
     * 创建 AI 绘图任务
     * @param createPictureOutPaintingTaskRequest 创建 AI 绘图任务请求参数
     * @param loginUser 登录用户对象
     * @return 创建 AI 绘图任务响应参数
     */
     CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser);

}
