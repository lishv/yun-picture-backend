package com.xiaoli.yunpicturebackend.manager.upload;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;

import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.xiaoli.yunpicturebackend.config.CosClientConfig;
import com.xiaoli.yunpicturebackend.exception.BusinessException;
import com.xiaoli.yunpicturebackend.exception.ErrorCode;

import com.xiaoli.yunpicturebackend.manager.CosManager;
import com.xiaoli.yunpicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;


import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;


/**
 * 图片上传模板类
 */
@Slf4j
public abstract class PictureUploadTemplate {
    @Resource
    private CosClientConfig cosClientConfig;
    @Resource
    private CosManager cosManager;

    public UploadPictureResult uploadPicture (Object inputSource,String uploadPathPrefix) {
        //1.校验图片
        validPicture(inputSource);
        //2.图片上传地址
        String uuid = RandomUtil.randomString(16);
        String originalFilename = getOriginalFilename(inputSource);//用户上传的原文件名
        //上传的文件名格式为 时间-随机数.文件后缀,并没有使用用户上传的文件名,因为用户上传的文件名可能会重复,导致上传失败
        String uploadFileName = String.format("%s-%s.%s", DateUtil.formatDate(new Date()), uuid, FileUtil.getSuffix(originalFilename));
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFileName);//上传的格式为 /上传文件夹/文件名
        //解析结果并返回
        //创建临时文件空间
        File file = null;
        try {
            //3.创建临时文件空间
            file= File.createTempFile(uploadPath,null);
            //处理文件来源,保存到临时文件空间文件
            processFile(inputSource,file);
            //4上传file到cos
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            //获取图片信息
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            //进行上传压缩后,获取图片处理结果
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            List<CIObject> objectList = processResults.getObjectList();//得到图片处理结果列表
            if(CollUtil.isNotEmpty(objectList)){
                //获取压缩之后得到文件信息
                CIObject compressedCiObject = objectList.get(0);//获取第一个图片处理结果
                CIObject thumbnailCiObject = null;
                if(objectList.size()>1) {
                    thumbnailCiObject = objectList.get(1);//获取第二个图片处理结果
                }
                //封装压缩图的返回结果
                return buildResult(originalFilename, compressedCiObject,thumbnailCiObject,imageInfo);
            }
            //5.若未压缩,获取上传图片的信息,返回上传图片的结果对象
            return buildResult(imageInfo, uploadPath, originalFilename, file);
        } catch (IOException e) {
            log.error("file upload ,filePath:{}",uploadPath,e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"文件上传失败");
        }finally {
            //6.删除临时文件空间
            deleteTempFile(file);
        }

    }


    /**
     * 获取用户上传的原文件名
     * @param inputSource   用户上传的文件来源(本地文件或者URL)
     * @return 用户上传的原文件名
     */
    public abstract String getOriginalFilename(Object inputSource) ;


    /**
     * 处理文件来源
     * @param inputSource   用户上传的文件来源(本地文件或者URL)
     */
    public abstract void processFile(Object inputSource,File file) throws IOException;



    /**
     * 校验图片
     * @param inputSource   用户上传的文件来源(本地文件或者URL)
     */
    public abstract void validPicture(Object inputSource);


    /**
     * 删除临时文件
     * @param file
     */
    public static void deleteTempFile(File file) {
        if(file==null)
            return;
        boolean deleteResult = file.delete();
        if(!deleteResult){
            log.error("file delete error ,filePath:{}",file.getAbsolutePath());

        }

    }

    /**
     * 封装返回结果
     * @param originalFilename 用户上传的原文件名
     * @param compressedCiObject 压缩后的图片信息
     * @param thumbnailCiObject 缩略图信息,若未压缩,则为缩略图信息
     * @param imageInfo 图片信息
     * @return 上传图片的结果对象
     */
    private UploadPictureResult buildResult(String originalFilename, CIObject compressedCiObject,CIObject thumbnailCiObject,ImageInfo imageInfo) {
        String format = compressedCiObject.getFormat();//图片格式,例如jpg,png等
        int picWidth = compressedCiObject.getWidth();//图片宽度
        int picHeight = compressedCiObject.getHeight();//图片高度
//            int quality = compressedCiObject.getQuality();//图片质量
//            String ave = compressedCiObject.getAve();//图片平均颜色
//            int orientation = compressedCiObject.getOrientation();//图片方向
//            int frameCount = compressedCiObject.getFrameCount();//图片帧数
        //图片比例
        double pictureScale= NumberUtil.round(picWidth*1.0/picHeight,2).doubleValue();
        //返回可以访问的文件地址
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        uploadPictureResult.setUrl(cosClientConfig.getHost()+"/"+ compressedCiObject.getKey());//这是压缩后的webp图片地址
        uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
        uploadPictureResult.setPicSize(compressedCiObject.getSize().longValue());
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(pictureScale);
        uploadPictureResult.setPicFormat(format);
        //图片主色调
        uploadPictureResult.setPicColor(imageInfo.getAve());
        //设置缩略图地址
        if(BeanUtil.isNotEmpty(thumbnailCiObject)){
            uploadPictureResult.setThumbnailUrl(cosClientConfig.getHost()+"/"+ thumbnailCiObject.getKey());//这是缩略图地址
        }

        return uploadPictureResult;
    }

    /**
 * 封装返回结果
 * @param imageInfo  图片信息
 * @param uploadPath 上传路径
 * @param originalFilename 用户上传的原文件名
 * @param file 文件
 * @return 上传图片的结果
 */
private UploadPictureResult buildResult(ImageInfo imageInfo, String uploadPath, String originalFilename, File file) {
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
    uploadPictureResult.setUrl(cosClientConfig.getHost()+"/"+ uploadPath);
    uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
    uploadPictureResult.setPicSize(FileUtil.size(file));
    uploadPictureResult.setPicWidth(picWidth);
    uploadPictureResult.setPicHeight(picHeight);
    uploadPictureResult.setPicScale(pictureScale);
    uploadPictureResult.setPicFormat(format);
    //图片主色调
    uploadPictureResult.setPicColor(imageInfo.getAve());

    return uploadPictureResult;
}

}
