package com.xiaoli.yunpicturebackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoli.yunpicturebackend.model.entity.Picture;
import com.xiaoli.yunpicturebackend.service.PictureService;
import com.xiaoli.yunpicturebackend.mapper.PictureMapper;
import org.springframework.stereotype.Service;

/**
* @author LiShuwen
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2026-08-06 23:05:07
*/
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{

}




