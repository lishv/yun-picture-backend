package com.xiaoli.yunpicturebackend.service;

import com.xiaoli.yunpicturebackend.model.dto.picture.PictureUploadRequest;
import com.xiaoli.yunpicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoli.yunpicturebackend.model.entity.User;
import com.xiaoli.yunpicturebackend.model.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

/**
* @author LiShuwen
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2026-08-06 23:05:07
*/
public interface PictureService extends IService<Picture> {
    public PictureVO uploadPicture(MultipartFile file, PictureUploadRequest pictureUploadRequest, User loginUser) ;
}
