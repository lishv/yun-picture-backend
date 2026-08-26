package com.xiaoli.yunpicturebackend.service.impl;
import java.io.IOException;
import java.util.List;
import java.util.Date;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoli.yunpicturebackend.exception.BusinessException;
import com.xiaoli.yunpicturebackend.exception.ErrorCode;
import com.xiaoli.yunpicturebackend.exception.ThrowUtils;
import com.xiaoli.yunpicturebackend.manager.FileManager;
import com.xiaoli.yunpicturebackend.manager.upload.FilePictureUpload;
import com.xiaoli.yunpicturebackend.manager.upload.PictureUploadTemplate;
import com.xiaoli.yunpicturebackend.manager.upload.UrlPictureUpload;
import com.xiaoli.yunpicturebackend.model.dto.file.UploadPictureResult;
import com.xiaoli.yunpicturebackend.model.dto.picture.*;
import com.xiaoli.yunpicturebackend.model.entity.Picture;
import com.xiaoli.yunpicturebackend.model.entity.User;
import com.xiaoli.yunpicturebackend.model.enums.PictureReviewStatusEnmu;
import com.xiaoli.yunpicturebackend.model.vo.CursorPageVO;
import com.xiaoli.yunpicturebackend.model.vo.PictureVO;
import com.xiaoli.yunpicturebackend.model.vo.UserVO;
import com.xiaoli.yunpicturebackend.service.PictureService;
import com.xiaoli.yunpicturebackend.mapper.PictureMapper;
import com.xiaoli.yunpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
* @author LiShuwen
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2026-08-06 23:05:07
*/
@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture> implements PictureService{
    @Resource
    private FileManager fileManager;
    @Resource
    private FilePictureUpload  filePictureUpload;
    @Resource
    private UrlPictureUpload urlPictureUpload;
    @Autowired
    private UserService userService;

    /**
     * 上传图片
     * @param inputSource 图片文件
     * @param pictureUploadRequest 图片上传请求参数
     * @param loginUser 登录用户对象
     * @return 图片VO对象
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        //校验参数
        ThrowUtils.throwIf(loginUser==null, ErrorCode.NO_AUTH_ERROR);
        //判断是新增还是删除
        Long pictureId =null;
        if(BeanUtil.isNotEmpty(pictureUploadRequest)){
            pictureId = pictureUploadRequest.getId();
        }
        //如果更新,判断图片是否存在且用户是否有权限编辑
        if(pictureId!=null){
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture==null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            //进本人或者管理员可编辑图片
            if(!oldPicture.getUserId().equals(loginUser.getId())&&!userService.isAdmin(loginUser)){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }



        }
        //上传图片,得到图片信息
        //按照用户的id划分目录
        String uploadPathPrefix=String.format("public/%s",loginUser.getId());
        //根据inputSource判断是文件上传还是url上传,兼容MultipartFile和String
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        //获取上传图片的结果
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource,uploadPathPrefix);
        //构造入库的图片信息
        Picture picture = new Picture();
        BeanUtil.copyProperties(uploadPictureResult,picture);
        if (pictureUploadRequest!=null&&StrUtil.isNotBlank(pictureUploadRequest.getSourceHash())) {
            picture.setSourceHash(pictureUploadRequest.getSourceHash());
        }
        //将分类和标签设置到图片对象
        if(pictureUploadRequest!=null&&StrUtil.isNotBlank(pictureUploadRequest.getCategory())){
            picture.setCategory(pictureUploadRequest.getCategory());
        }
        if(pictureUploadRequest!=null&&StrUtil.isNotBlank(pictureUploadRequest.getTags())){
            picture.setTags(JSONUtil.toJsonStr(pictureUploadRequest.getTags()));
        }
        /**
         * 支持外层自定义图片名称
         */
        String picName= uploadPictureResult.getPicName();
        if(pictureUploadRequest!=null&&StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
          picName=pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setUserId(loginUser.getId());
        //填充审核参数
        fillReviewParams(picture, loginUser);
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
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Date reviewTime = pictureQueryRequest.getReviewTime();

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
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
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
    public Page<PictureVO> getPictureVOPage(
            Page<Picture> picturePage,
            HttpServletRequest request
    ) {
        List<Picture> pictureList = picturePage.getRecords();

        Page<PictureVO> pictureVOPage = new Page<>(
                picturePage.getCurrent(),
                picturePage.getSize(),
                picturePage.getTotal()
        );

        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }

        // 将图片实体转换为图片 VO。
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());

        // 获取图片创建者 ID，批量查询用户，避免循环查询数据库。
        Set<Long> userIdSet = pictureList.stream()
                .map(Picture::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (CollUtil.isEmpty(userIdSet)) {
            pictureVOPage.setRecords(pictureVOList);
            return pictureVOPage;
        }

        List<User> userList = userService.listByIds(userIdSet);

        Map<Long, List<User>> userIdUserListMap = userList.stream()
                .collect(Collectors.groupingBy(User::getId));

        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;

            if (userId != null && userIdUserListMap.containsKey(userId)) {
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
     * @param picture 图片实体类
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
    /**
     * 使用复合游标查询图片列表。
     *
     * @param cursorRequest 游标查询参数
     * @param request HTTP 请求
     * @return 游标查询结果
     */
    @Override
    public CursorPageVO<PictureVO> listPictureVOByCursor(
            PictureCursorQueryRequest cursorRequest,
            HttpServletRequest request
    ) {
        int pageSize = cursorRequest.getPageSize() == null
                ? 20
                : cursorRequest.getPageSize();

        ThrowUtils.throwIf(
                pageSize <= 0 || pageSize > 20,
                ErrorCode.PARAMS_ERROR,
                "每次加载数量必须在 1 到 20 之间"
        );

        boolean hasCursorCreateTime = cursorRequest.getCursorCreateTime() != null;
        boolean hasCursorId = cursorRequest.getCursorId() != null;

        // createTime 和 id 必须同时存在或同时不存在。
        ThrowUtils.throwIf(hasCursorCreateTime != hasCursorId, ErrorCode.PARAMS_ERROR, "游标参数必须同时提供");
        // 复用已有的图片搜索条件。
        PictureQueryRequest queryRequest = new PictureQueryRequest();

        queryRequest.setSearchText(cursorRequest.getSearchText());
        queryRequest.setCategory(cursorRequest.getCategory());
        queryRequest.setTags(cursorRequest.getTags());
        //设置搜索的审核状态，默认查询已审核的图片
        queryRequest.setReviewStatus(cursorRequest.getReviewStatus());
        QueryWrapper<Picture> queryWrapper = getQueryWrapper(queryRequest);

        //在上一批图片的基础条件上查询下一批图片
        // 下一批数据：
        // createTime 小于上一批最后一项，
        // 或 createTime 相同但 id 更小。
        if (hasCursorCreateTime) {
            queryWrapper.and(wrapper ->
                    wrapper
                            .lt("createTime", cursorRequest.getCursorCreateTime())
                            .or(nested -> nested.eq("createTime", cursorRequest.getCursorCreateTime())
                                    .lt("id", cursorRequest.getCursorId())
                            )

            );
        }

        queryWrapper
                .orderByDesc("createTime")
                .orderByDesc("id")
                // 多查询一项，用于判断还有没有下一批。
                .last("LIMIT " + (pageSize + 1));

        List<Picture> queriedPictures =
                this.list(queryWrapper);

        boolean hasMore = queriedPictures.size() > pageSize;

        List<Picture> currentPictures;

        if (hasMore) {currentPictures = new ArrayList<>(
                queriedPictures.subList(0, pageSize)
            );
        } else {
            currentPictures = new ArrayList<>(queriedPictures);
        }

        /*
         * 复用已有的 getPictureVOPage，
         * 完成 PictureVO 转换和用户信息关联。
         * 这一步不会再次查询 picture 表。
         */
        Page<Picture> cursorPicturePage = new Page<>(1, pageSize);

        cursorPicturePage.setRecords(currentPictures);
        cursorPicturePage.setTotal(currentPictures.size());

        Page<PictureVO> cursorPictureVOPage = getPictureVOPage(cursorPicturePage, request);

        CursorPageVO<PictureVO> result = new CursorPageVO<>();

        result.setRecords(cursorPictureVOPage.getRecords());
        result.setHasMore(hasMore);

        if (hasMore && !currentPictures.isEmpty()) {
            Picture lastPicture = currentPictures.get(
                    currentPictures.size() - 1
            );

            result.setNextCursorCreateTime(
                    lastPicture.getCreateTime()
            );
            result.setNextCursorId(
                    lastPicture.getId()
            );
        }

        return result;
    }

    /**
     * 图片审核
     * @param pictureReviewRequest  审核图片请求参数
     * @param loginUser 登录用户实体
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 1.校验参数
        ThrowUtils.throwIf(pictureReviewRequest==null, ErrorCode.PARAMS_ERROR);
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnmu pictureReviewStatusEnmu = PictureReviewStatusEnmu.getEnumByValue(reviewStatus);
        String reviewMessage = pictureReviewRequest.getReviewMessage();
        if(id==null||pictureReviewStatusEnmu==null||PictureReviewStatusEnmu.REVIEWING.equals(pictureReviewStatusEnmu)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2.校验图片是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture==null, ErrorCode.PARAMS_ERROR);
        // 3.校验审核状态是否有效
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"请勿重复审核");
        }
        //4.数据库操作
        Picture newPicture = new Picture();
        BeanUtil.copyProperties(pictureReviewRequest, newPicture);
        newPicture.setReviewerId(loginUser.getId());
        newPicture.setReviewTime(new Date());
        boolean result = this.updateById(newPicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);


    }

    /**
     * 填充审核参数
     * @param picture   图片实体
     * @param loginUser 登录用户实体
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {

        if (userService.isAdmin(loginUser)) {
            //管理员默认通过审核
            picture.setReviewStatus(PictureReviewStatusEnmu.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewTime(new Date());
            picture.setReviewMessage("管理员自动过审");
        }else{
            //非管理员无论编辑,创建,都需要审核
            picture.setReviewStatus(PictureReviewStatusEnmu.REVIEWING.getValue());
        }



    }

    /**
     * 批量上传图片
     * @param pictureUploadByBatchRequest 图片上传批量请求参数
     * @param loginUser 登录用户对象
     * @return 上传成功数量
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        //1.校验参数
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        Integer offset = pictureUploadByBatchRequest.getOffset();
        if (offset == null) {
            offset=0;
        }
        ThrowUtils.throwIf(searchText==null||count==null||count<=0||count>30||offset<0, ErrorCode.PARAMS_ERROR);
        //2.抓取内容
        String fetchUrl=String.format(
                "https://cn.bing.com/images/async?q=%s&mmasync=1&first=%d&count=%d",
                searchText,offset+1,count);
        //jsoup解析html文档
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();

        } catch (IOException e) {
            log.error("获取页面失败",e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"获取页面失败，请稍后重试");
        }
        //3.解析内容
        Element div = document.getElementsByClass("dgControl").first();//div
        if (div==null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"获取元素失败");
        }
        //选取图片标签且class为mimg的
        Elements imgElementList = div.select("img.mimg");
        //遍历元素,依次上传图片
        int uploadCount = 0;//上传成功数量
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if(StrUtil.isBlank(namePrefix)){
            namePrefix=searchText;
        }
        for(Element imgElement : imgElementList){
            //获取图片url
            String fileUrl = imgElement.attr("src");//图片的属性为src的内容(即Url)
            if(StrUtil.isBlank(fileUrl)){
                log.info("当前链接为空,已跳过:{}",fileUrl);
                continue;
            }
            //处理图片地址,防止转义或对象存储冲突问题
            //dadw123.com?a=1&b=2.应该只保留前面dadw123.com
            int questionMarkIndex = fileUrl.indexOf("?");
            if(questionMarkIndex>-1){
                fileUrl=fileUrl.substring(0,questionMarkIndex);
            }

            String sourceHash = getSourceHash(fileUrl);
            if (existsActivePictureBySourceHash(sourceHash)) {
                log.info("当前有效图片已经存在，跳过，sourceHash={}, url={}", sourceHash, fileUrl);
                continue;
            }

            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setFileUrl(fileUrl);
            pictureUploadRequest.setPicName(namePrefix+(uploadCount+1));
            // 设置后端内部字段，客户端不能直接传入
            pictureUploadRequest.setSourceHash(sourceHash);
            //将本批次照片统一设置为相同分类和标签
            pictureUploadRequest.setCategory(pictureUploadByBatchRequest.getCategory());
            pictureUploadRequest.setTags(pictureUploadByBatchRequest.getTags());
            //4.上传图片
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功,id={},url={},sourceHash={}",pictureVO.getId(),pictureVO.getUrl(),sourceHash);
                uploadCount++;
            } catch (DuplicateKeyException e) {
                // 数据库唯一索引处理并发重复
                log.info("图片来源已经存在，跳过重复数据，sourceHash={}, url={}", sourceHash, fileUrl);
                continue;
            } catch (Exception e) {
                log.error("图片上传失败，sourceHash={}, url={}", sourceHash, fileUrl, e);
                continue;
            }
            //终止条件
            if(uploadCount>=count){
                break;
            }

        }
        return uploadCount;
    }
    /**
     * 判断有效图片是否已经存在相同来源
     */
    private boolean existsActivePictureBySourceHash(String sourceHash) {
        if (StrUtil.isBlank(sourceHash)) {
            return false;
        }
        return this.lambdaQuery()
                .eq(Picture::getSourceHash, sourceHash)
                .eq(Picture::getIsDelete, 0)
                .count() > 0;
    }

    /**
     * 获取图片来源哈希值
     * @param fileUrl 图片URL
     * @return 图片来源哈希值
     */
    private String getSourceHash( String fileUrl) {
        if (StrUtil.isBlank(fileUrl)) {
            return null;
        }
        return DigestUtil.sha256Hex(fileUrl.trim());
    }
}




