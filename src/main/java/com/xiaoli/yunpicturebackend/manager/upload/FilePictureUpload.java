package com.xiaoli.yunpicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import com.xiaoli.yunpicturebackend.exception.ErrorCode;
import com.xiaoli.yunpicturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 文件上传类
 */
@Service
public class FilePictureUpload extends PictureUploadTemplate {
    @Override
    public String getOriginalFilename(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        return multipartFile.getOriginalFilename();//用户上传的原文件名
    }

    @Override
    public void processFile(Object inputSource, File file) throws IOException {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        //将文件写入临时文件
        multipartFile.transferTo(file);
    }

    @Override
    public void validPicture(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
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
}
