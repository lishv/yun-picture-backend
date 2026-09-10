package com.xiaoli.yunpicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.xiaoli.yunpicture.infrastructure.exception.BusinessException;
import com.xiaoli.yunpicture.infrastructure.exception.ErrorCode;
import com.xiaoli.yunpicture.infrastructure.exception.ThrowUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

@Service
public class UrlPictureUpload extends PictureUploadTemplate {
    @Override
    public String getOriginalFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        //http://example.com/image.jpg -> image.jpg
        return FileUtil.mainName(fileUrl);
    }

    @Override
    public void processFile(Object inputSource, File file) throws IOException {
        String fileUrl = (String) inputSource;
        //下载文件到临时文件
        HttpUtil.downloadFile(fileUrl,file);


    }

    @Override
    public void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        //1.校验URL是否为空
        ThrowUtils.throwIf(StringUtils.isBlank(fileUrl), ErrorCode.PARAMS_ERROR,"图片URL不能为空");
        //2.校验URL格式
        try {
            new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件地址格式不正常");
        }
        //3.校验URL协议
        ThrowUtils.throwIf(!fileUrl.startsWith("http://")&&!fileUrl.startsWith("https://"), ErrorCode.PARAMS_ERROR,"仅支持http或https协议的文件地址");
        HttpResponse httpResponse = null;
        //4.发送Head请求校验文件是否存在
        try{
            httpResponse = HttpUtil.createRequest(Method.HEAD, fileUrl).execute();
            //为正常返回,无需执行其他判断,因为某些服务器不支持head请求,导致返回405错误
            if (httpResponse.getStatus()!= HttpStatus.HTTP_OK) {
                return;
            }
            //5.文件存在,文件类型校验,文件大小校验
            String contentType = httpResponse.header("Content-Type");
            if(StrUtil.isNotEmpty(contentType)){
                //允许图片类型
                final List<String> ALLOW_CONTENT = Arrays.asList("image/jpeg","image/png","image/gif","image/webp","image/jpg");
                ThrowUtils.throwIf(!ALLOW_CONTENT.contains(contentType), ErrorCode.PARAMS_ERROR,"文件类型错误");
                //文件大小校验
                String contentLengthStr = httpResponse.header("Content-Length");
                if (StrUtil.isNotEmpty(contentLengthStr)) {
                    try{

                        long contentLength = Long.parseLong(contentLengthStr);
                        final long ONE_M=1024*1024;
                        ThrowUtils.throwIf(contentLength>8*ONE_M,ErrorCode.PARAMS_ERROR,"文件大小不能超过8MB");

                    }catch (Exception e){
                        throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件大小格式异常");
                    }
                }
            }
        } finally{

            if(httpResponse!=null){
                httpResponse.close();
            }
        }



    }
}
