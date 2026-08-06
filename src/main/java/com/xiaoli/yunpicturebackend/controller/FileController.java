package com.xiaoli.yunpicturebackend.controller;

import cn.hutool.core.io.IoUtil;
import cn.hutool.http.server.HttpServerResponse;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.transfer.Download;
import com.qcloud.cos.utils.IOUtils;
import com.xiaoli.yunpicturebackend.annotation.AuthCheck;
import com.xiaoli.yunpicturebackend.common.BaseResponse;
import com.xiaoli.yunpicturebackend.common.ResultUtils;
import com.xiaoli.yunpicturebackend.constant.UserConstant;
import com.xiaoli.yunpicturebackend.exception.BusinessException;
import com.xiaoli.yunpicturebackend.exception.ErrorCode;
import com.xiaoli.yunpicturebackend.manager.CosManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {
    @Resource
    private CosManager cosManager;

    /**
     * 测试上传文件
     * @param multipartFile 文件
     * @return 文件路径
     */
    @PostMapping("/test/upload")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<String> testUpload(@RequestPart MultipartFile multipartFile) {
        //文件目录
        String fileName = multipartFile.getOriginalFilename();
        String filePath = String.format("/test/%s",fileName);
        File file = null;
        try {
            //上传文件
            file= File.createTempFile(filePath,null);
            //将文件写入临时文件
            multipartFile.transferTo(file);
            //上传file到cos
            cosManager.putObject(filePath, file);
            //返回可以访问的文件地址
            return ResultUtils.success(filePath);
        } catch (IOException e) {
            log.error("file upload ,filePath:{}",filePath,e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"文件上传失败");
        }finally {
            if(file!=null){
                //删除临时文件
                boolean delete = file.delete();
                if(!delete){
                    log.error("file delete error ,filePath:{}",filePath);
                }
            }
        }

    }
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/test/download")
    public  void testDownload(String filePath, HttpServletResponse response) throws IOException {
        //打开流,为了关闭流
        COSObjectInputStream cosObjectInputStream = null;
        try {
            COSObject cosObject = cosManager.getObject(filePath);
            cosObjectInputStream = cosObject.getObjectContent();
            byte[]bytes = IOUtils.toByteArray(cosObjectInputStream);//cos自带工具类，将输入流转换为字节数组
            //设置响应头,告诉浏览器这是一个文件,而不是文本
            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + filePath);

            //写入响应
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }finally {
            //关闭输入流
            if(cosObjectInputStream!=null){
                cosObjectInputStream.close();
            }
        }



    }
}
