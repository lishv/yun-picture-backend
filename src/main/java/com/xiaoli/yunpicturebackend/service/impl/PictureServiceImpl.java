package com.xiaoli.yunpicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoli.yunpicturebackend.exception.ErrorCode;
import com.xiaoli.yunpicturebackend.exception.ThrowUtils;
import com.xiaoli.yunpicturebackend.manager.FileManager;
import com.xiaoli.yunpicturebackend.model.dto.file.UploadPictureResult;
import com.xiaoli.yunpicturebackend.model.dto.picture.PictureUploadRequest;
import com.xiaoli.yunpicturebackend.model.entity.Picture;
import com.xiaoli.yunpicturebackend.model.entity.User;
import com.xiaoli.yunpicturebackend.model.vo.PictureVO;
import com.xiaoli.yunpicturebackend.service.PictureService;
import com.xiaoli.yunpicturebackend.mapper.PictureMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Date;

/**
* @author LiShuwen
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2026-08-06 23:05:07
*/
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture> implements PictureService{
    @Resource
    private FileManager fileManager;
    @Override
    public PictureVO uploadPicture(MultipartFile file, PictureUploadRequest pictureUploadRequest, User loginUser) {
        //校验参数
        ThrowUtils.throwIf(loginUser==null, ErrorCode.NO_AUTH_ERROR);
        //判断是新增还是删除
        Long pictureId =null;
        if(BeanUtil.isNotEmpty(pictureUploadRequest)){
            pictureId = pictureUploadRequest.getId();
        }
        //如果更新,判断图片是否存在
        if(pictureId!=null){
            boolean exists = this.lambdaQuery().eq(Picture::getId, pictureId).exists();
            ThrowUtils.throwIf(!exists, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }
        //上传图片,得到图片信息
        //按照用户的id划分目录
        String uploadPathPrefix=String.format("public/%s",loginUser.getId());
        UploadPictureResult uploadPictureResult = fileManager.uploadPicture(file,uploadPathPrefix);
        //构造入库的图片信息
        Picture picture = new Picture();
        BeanUtil.copyProperties(uploadPictureResult,picture);
        picture.setName(uploadPictureResult.getPicName());
        picture.setUserId(loginUser.getId());

        //操作数据库
        //如果pictureId 不为空,上传表示更新,否则新增
        if(pictureId!=null){
            //如果更新,需要补充id和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        //更新或者新增
        boolean result = this.saveOrUpdate(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败,数据库操作失败");

        return PictureVO.objToVo(picture);

    }
}




