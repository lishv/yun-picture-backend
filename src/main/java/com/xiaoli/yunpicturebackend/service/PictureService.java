package com.xiaoli.yunpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoli.yunpicturebackend.model.dto.picture.PictureCursorQueryRequest;
import com.xiaoli.yunpicturebackend.model.dto.picture.PictureQueryRequest;
import com.xiaoli.yunpicturebackend.model.dto.picture.PictureUploadRequest;
import com.xiaoli.yunpicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoli.yunpicturebackend.model.entity.User;
import com.xiaoli.yunpicturebackend.model.vo.CursorPageVO;
import com.xiaoli.yunpicturebackend.model.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
* @author LiShuwen
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2026-08-06 23:05:07
*/
public interface PictureService extends IService<Picture> {
    /**
     * 上传图片
     * @param file 图片文件
     * @param pictureUploadRequest 图片上传请求参数
     * @param loginUser 登录用户对象
     * @return 图片VO类
     */
    public PictureVO uploadPicture(MultipartFile file, PictureUploadRequest pictureUploadRequest, User loginUser) ;

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
}
