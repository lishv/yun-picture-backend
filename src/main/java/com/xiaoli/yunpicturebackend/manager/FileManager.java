package com.xiaoli.yunpicturebackend.manager;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.xiaoli.yunpicture.infrastructure.api.CosManager;
import com.xiaoli.yunpicture.infrastructure.config.CosClientConfig;
import com.xiaoli.yunpicture.infrastructure.exception.BusinessException;
import com.xiaoli.yunpicture.infrastructure.exception.ErrorCode;
import com.xiaoli.yunpicture.infrastructure.exception.ThrowUtils;
import com.xiaoli.yunpicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.*;

@Service
@Slf4j
@Deprecated //已废弃,请使用upload包的方法
public class FileManager {
    @Resource
    private CosClientConfig cosClientConfig;
    @Resource
    private CosManager cosManager;
public UploadPictureResult uploadPicture (MultipartFile multipartFile,String uploadPathPrefix) {
        //校验图片
        validPicture(multipartFile);
        //图片上传地址
        String uuid = RandomUtil.randomString(16);
        String originalFilename = multipartFile.getOriginalFilename();//用户上传的原文件名
        //上传的文件名格式为 时间-随机数.文件后缀,并没有使用用户上传的文件名,因为用户上传的文件名可能会重复,导致上传失败
        String uploadFileName = String.format("%s-%s.%s", DateUtil.formatDate(new Date()), uuid, FileUtil.getSuffix(originalFilename));
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFileName);//上传的格式为 /上传文件夹/文件名
        //解析结果并返回
        //创建临时文件空间
        File file = null;
        try {
            //上传文件
            file= File.createTempFile(uploadPath,null);
            //将文件写入临时文件
            multipartFile.transferTo(file);
            //上传file到cos
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            //获取上传图片的信息
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            String format = imageInfo.getFormat();//图片格式,例如jpg,png等
            int picWidth = imageInfo.getWidth();//图片宽度
            int picHeight = imageInfo.getHeight();//图片高度
//            int quality = imageInfo.getQuality();//图片质量
//            String ave = imageInfo.getAve();//图片平均颜色
//            int orientation = imageInfo.getOrientation();//图片方向
//            int frameCount = imageInfo.getFrameCount();//图片帧数
            //图片比例
            double pictureScale= NumberUtil.round(picWidth*1.0/picHeight,2).doubleValue();
            //返回可以访问的文件地址
            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            uploadPictureResult.setUrl(cosClientConfig.getHost()+"/"+uploadPath);
            uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
            uploadPictureResult.setPicSize(FileUtil.size(file));
            uploadPictureResult.setPicWidth(picWidth);
            uploadPictureResult.setPicHeight(picHeight);
            uploadPictureResult.setPicScale(pictureScale);
            uploadPictureResult.setPicFormat(format);

            return uploadPictureResult;
        } catch (IOException e) {
            log.error("file upload ,filePath:{}",uploadPath,e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"文件上传失败");
        }finally {
            deleteTempFile(file);
        }

}



    /**
     * 校验图片
     * @param multipartFile 上传的图片文件
     */
    private  void validPicture(MultipartFile multipartFile){
    ThrowUtils.throwIf(multipartFile.isEmpty(), ErrorCode.PARAMS_ERROR,"文件不能为空");
        //1.校验文件大小
        long fileSize = multipartFile.getSize();
        final long ONE_M=1024*1024;
        ThrowUtils.throwIf(fileSize>8*ONE_M,ErrorCode.PARAMS_ERROR,"文件大小不能超过8MB");
        //2.文件后缀校验
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        //3.允许上传的文件后缀列表
        final List<String > ALLOW_FORMAT_LIST = Arrays.asList("jpg","jpeg","png","gif","webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(suffix),ErrorCode.PARAMS_ERROR,"文件类型错误");

    }
    /**
     * 删除临时文件
     * @param file  临时文件
     */
    private static void deleteTempFile(File file) {
        if(file==null)
            return;
        boolean deleteResult = file.delete();
        if(!deleteResult){
            log.error("file delete error ,filePath:{}",file.getAbsolutePath());

        }

    }

}
