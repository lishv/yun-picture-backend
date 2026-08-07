package com.xiaoli.yunpicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoli.yunpicturebackend.exception.ErrorCode;
import com.xiaoli.yunpicturebackend.exception.ThrowUtils;
import com.xiaoli.yunpicturebackend.manager.FileManager;
import com.xiaoli.yunpicturebackend.model.dto.file.UploadPictureResult;
import com.xiaoli.yunpicturebackend.model.dto.picture.PictureQueryRequest;
import com.xiaoli.yunpicturebackend.model.dto.picture.PictureUploadRequest;
import com.xiaoli.yunpicturebackend.model.entity.Picture;
import com.xiaoli.yunpicturebackend.model.entity.User;
import com.xiaoli.yunpicturebackend.model.vo.PictureVO;
import com.xiaoli.yunpicturebackend.model.vo.UserVO;
import com.xiaoli.yunpicturebackend.service.PictureService;
import com.xiaoli.yunpicturebackend.mapper.PictureMapper;
import com.xiaoli.yunpicturebackend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author LiShuwen
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2026-08-06 23:05:07
*/
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture> implements PictureService{
    @Resource
    private FileManager fileManager;
    @Autowired
    private UserService userService;

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

    /**
     *获取查询条件对象
     * @param pictureQueryRequest 查询参数
     * @return 查询条件对象
     */
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    /**
     * 获取图片包装类(单条)
     * @param picture 图片实体类
     * @param request HTTP请求对象
     * @return 图片VO类
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request){
        //对象转换成封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        Long userId = picture.getUserId();
        if(userId!=null&&userId>0){
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    /**
     * 获取图片包装类(多条)
     * @param picturePage 分页对象
     * @param request HTTP请求对象
     * @return 分页对象,包含图片VO类列表
     */
    @Override
    public Page<PictureVO>getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request){
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if(CollUtil.isEmpty(pictureList)){
            return pictureVOPage;
        }
        //内部转换为VO类
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).toList();
        //1.关联查询用户,因为内部的uservo是空的,所以需要关联查询用户信息
        Set<Long> userIdSet = pictureList.stream().map(Picture::getId).collect(Collectors.toSet());
        List<User> userList = userService.listByIds(userIdSet);
        //根据用户id分组
        Map<Long, List<User>> userIdUserListMap = userList.stream().collect(Collectors.groupingBy(User::getId));
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user=null;
            if(userIdUserListMap.containsKey(userId)){
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;

    }

    /**
     * 校验图片数据
     * 1. 校验图片实体类是否为空
     * 2. 校验图片实体类的字段是否为空
     * 3. 校验图片实体类的字段是否超过最大长度
     * 4. 校验图片实体类的字段是否超过最大长度
     * @param picture
     */
    @Override
    public void validatePicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }
}




