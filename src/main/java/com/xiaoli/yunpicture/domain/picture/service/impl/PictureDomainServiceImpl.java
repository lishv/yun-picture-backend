package com.xiaoli.yunpicture.domain.picture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xiaoli.yunpicture.application.service.SpaceApplicationService;
import com.xiaoli.yunpicture.domain.picture.entity.Picture;
import com.xiaoli.yunpicture.domain.picture.repository.PictureRepository;
import com.xiaoli.yunpicture.domain.picture.service.PictureDomainService;
import com.xiaoli.yunpicture.domain.picture.valueobject.PictureReviewStatusEnmu;
import com.xiaoli.yunpicture.domain.user.entity.User;
import com.xiaoli.yunpicture.infrastructure.api.CosManager;
import com.xiaoli.yunpicture.infrastructure.api.aliyunai.AliYunAiApi;
import com.xiaoli.yunpicture.infrastructure.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.xiaoli.yunpicture.infrastructure.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.xiaoli.yunpicture.infrastructure.common.BaseResponse;
import com.xiaoli.yunpicture.infrastructure.common.ResultUtils;
import com.xiaoli.yunpicture.infrastructure.exception.BusinessException;
import com.xiaoli.yunpicture.infrastructure.exception.ErrorCode;
import com.xiaoli.yunpicture.infrastructure.exception.ThrowUtils;
import com.xiaoli.yunpicture.infrastructure.messaging.picture.PictureCleanupPublisher;
import com.xiaoli.yunpicture.infrastructure.messaging.picture.PictureCleanupTaskService;
import com.xiaoli.yunpicture.infrastructure.utils.ColorSimilarUtils;
import com.xiaoli.yunpicture.interfaces.dto.picture.*;
import com.xiaoli.yunpicture.interfaces.vo.picture.PictureVO;
import com.xiaoli.yunpicture.infrastructure.manager.upload.FilePictureUpload;
import com.xiaoli.yunpicture.infrastructure.manager.upload.PictureUploadTemplate;
import com.xiaoli.yunpicture.infrastructure.manager.upload.UrlPictureUpload;
import com.xiaoli.yunpicture.infrastructure.manager.upload.model.dto.file.UploadPictureResult;
import com.xiaoli.yunpicture.domain.space.entity.Space;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author LiShuwen
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2026-08-06 23:05:07
 */
@Slf4j
@Service
public class PictureDomainServiceImpl  implements PictureDomainService {
    @Resource
    private FilePictureUpload filePictureUpload;
    @Resource
    private UrlPictureUpload urlPictureUpload;
    @Resource
    private SpaceApplicationService spaceApplicationService;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private CosManager cosManager;
    @Resource
    private AliYunAiApi aliYunAiApi;
    @Resource
    private PictureRepository pictureRepository;
    @Resource
    private PictureCleanupTaskService pictureCleanupTaskService;

    @Resource
    private PictureCleanupPublisher pictureCleanupPublisher;

    /**
     * 上传图片/更新图片
     *
     * @param inputSource          图片文件
     * @param pictureUploadRequest 图片上传请求参数
     * @param loginUser            登录用户对象
     * @return 图片VO对象
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        //校验参数
        //校验空间是否存在,是否与空间的权限
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            Space space = spaceApplicationService.getById(spaceId);
            if (BeanUtil.isEmpty(space)) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            }
            //改为使用统一的权限
//            //校验空间是否有权限,仅空间管理员可上传图片
//            if (!loginUser.getId().equals(space.getUserId())) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
            //校验额度
            if (space.getTotalCount() > space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() > space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }

        }

        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        //判断是新增还是删除
        Long pictureId = null;
        if (BeanUtil.isNotEmpty(pictureUploadRequest)) {
            pictureId = pictureUploadRequest.getId();
        }
        //如果更新,判断图片是否存在且用户是否有权限编辑
        Picture oldPicture = null;
        if (pictureId != null) {
            oldPicture = pictureRepository.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            //改为使用统一的权限
//            //仅本人或者管理员可编辑图片
//            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
            //校验空间是否与图片所属空间一致
            //没传空间id,则默认使用图片所属空间(这样也兼容公共空间)
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                //如果用户传了spaceId,则校验是否与图片所属空间一致
                if (ObjUtil.notEqual(oldPicture.getSpaceId(), spaceId)) {
                    throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片所属空间与传入空间不一致");
                }
            }
        }
        //上传图片,得到图片信息
        //按照用户的id划分目录=>按照空间划分目录
        String uploadPathPrefix;
        if (spaceId == null) {//公共图库
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        } else {//私有空间
            uploadPathPrefix = String.format("space/%s", spaceId);
        }
        //根据inputSource判断是文件上传还是url上传,兼容MultipartFile和String
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        //获取上传图片的结果
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        //构造入库的图片信息
        Picture picture = new Picture();
        picture.setSpaceId(spaceId);//指定空间id
        BeanUtil.copyProperties(uploadPictureResult, picture);
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getSourceHash())) {
            picture.setSourceHash(pictureUploadRequest.getSourceHash());
        }
        //将分类和标签设置到图片对象
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getCategory())) {
            picture.setCategory(pictureUploadRequest.getCategory());
        }
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getTags())) {
            picture.setTags(JSONUtil.toJsonStr(pictureUploadRequest.getTags()));
        }
        //支持外层自定义图片名称
        String picName = uploadPictureResult.getPicName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);//图片名称
        picture.setUserId(loginUser.getId());//用户id
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());//缩略图地址
        picture.setPicColor(uploadPictureResult.getPicColor());//图片主色调
        //填充审核参数
        fillReviewParams(picture, loginUser);
        //操作数据库
        //如果pictureId 不为空,上传表示更新,否则新增
        if (pictureId != null) {
            //如果更新,需要补充id和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        //开启事务,同时更新空间的额度与图片的信息
        Long finalSpaceId = spaceId;
        Long finalPictureId = pictureId;
        Picture finalOldPicture = oldPicture;
        Picture newPicture = transactionTemplate.execute(status -> {
            //更新或者新增图片信息
            boolean result = pictureRepository.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败,数据库操作失败");
            updateSpaceInfoAndDeleteOldPicture(finalOldPicture, finalPictureId, finalSpaceId, picture);
            return picture;
        });
        return PictureVO.objToVo(newPicture);
    }

    // 更新空间的容量与图片数量,仅私有空间才需要更新
    private void updateSpaceInfoAndDeleteOldPicture(Picture finalOldPicture, Long finalPictureId, Long finalSpaceId, Picture picture) {
        if (finalSpaceId != null) {
            if (finalPictureId != null && BeanUtil.isNotEmpty(finalOldPicture)) {
                //如果是更新,可以更新内存中的图片信息,并可以自行删除旧图片文件
                boolean update = spaceApplicationService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize=totalSize-" + finalOldPicture.getPicSize() + "+" + picture.getPicSize())
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
                cosManager.deleteObject(finalOldPicture.getUrl());
            } else {
                //上传新增图片,则更新空间的容量与图片数量
                boolean update = spaceApplicationService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize=totalSize+" + picture.getPicSize())
                        .setSql("totalCount=totalCount+1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
        }
    }

    /**
     * 获取查询条件对象
     *
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
        Long spaceId = pictureQueryRequest.getSpaceId();
        Boolean nullSpaceId = pictureQueryRequest.getNullSpaceId();

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
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        //根据nullSpaceId为true还是false或null查询是否查询公共图片
        queryWrapper.isNull(nullSpaceId, "spaceId");
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
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
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
     * 图片审核
     *
     * @param pictureReviewRequest 审核图片请求参数
     * @param loginUser            登录用户实体
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 1.校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnmu pictureReviewStatusEnmu = PictureReviewStatusEnmu.getEnumByValue(reviewStatus);
        String reviewMessage = pictureReviewRequest.getReviewMessage();
        if (id == null || pictureReviewStatusEnmu == null || PictureReviewStatusEnmu.REVIEWING.equals(pictureReviewStatusEnmu)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2.校验图片是否存在
        Picture oldPicture = pictureRepository.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.PARAMS_ERROR);
        // 3.校验审核状态是否有效
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        //4.数据库操作
        Picture newPicture = new Picture();
        BeanUtil.copyProperties(pictureReviewRequest, newPicture);
        newPicture.setReviewerId(loginUser.getId());
        newPicture.setReviewTime(new Date());
        boolean result = pictureRepository.updateById(newPicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);


    }

    /**
     * 填充审核参数
     *
     * @param picture   图片实体
     * @param loginUser 登录用户实体
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {

        if (loginUser.isAdmin()) {
            //管理员默认通过审核
            picture.setReviewStatus(PictureReviewStatusEnmu.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewTime(new Date());
            picture.setReviewMessage("管理员自动过审");
        } else {
            //非管理员无论编辑,创建,都需要审核
            picture.setReviewStatus(PictureReviewStatusEnmu.REVIEWING.getValue());
        }


    }

    /**
     * 批量上传图片
     *
     * @param pictureUploadByBatchRequest 图片上传批量请求参数
     * @param loginUser                   登录用户对象
     * @return 上传成功数量
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        //1.校验参数
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        Integer offset = pictureUploadByBatchRequest.getOffset();
        if (offset == null) {
            offset = 0;
        }
        ThrowUtils.throwIf(searchText == null || count == null || count <= 0 || count > 30 || offset < 0, ErrorCode.PARAMS_ERROR);
        //2.抓取内容
        String fetchUrl = String.format(
                "https://cn.bing.com/images/async?q=%s&mmasync=1&first=%d&count=%d",
                searchText, offset + 1, count);
        //jsoup解析html文档
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();

        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败，请稍后重试");
        }
        //3.解析内容
        Element div = document.getElementsByClass("dgControl").first();//div
        if (div == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        //选取图片标签且class为mimg的
        Elements imgElementList = div.select("img.mimg");
        //遍历元素,依次上传图片
        int uploadCount = 0;//上传成功数量
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        for (Element imgElement : imgElementList) {
            //获取图片url
            String fileUrl = imgElement.attr("src");//图片的属性为src的内容(即Url)
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空,已跳过:{}", fileUrl);
                continue;
            }
            //处理图片地址,防止转义或对象存储冲突问题
            //dadw123.com?a=1&b=2.应该只保留前面dadw123.com
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }

            String sourceHash = getSourceHash(fileUrl);
            if (existsActivePictureBySourceHash(sourceHash)) {
                log.info("当前有效图片已经存在，跳过，sourceHash={}, url={}", sourceHash, fileUrl);
                continue;
            }

            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setFileUrl(fileUrl);
            pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            // 设置后端内部字段，客户端不能直接传入
            pictureUploadRequest.setSourceHash(sourceHash);
            //将本批次照片统一设置为相同分类和标签
            pictureUploadRequest.setCategory(pictureUploadByBatchRequest.getCategory());
            pictureUploadRequest.setTags(pictureUploadByBatchRequest.getTags());
            //4.上传图片
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功,id={},url={},sourceHash={}", pictureVO.getId(), pictureVO.getUrl(), sourceHash);
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
            if (uploadCount >= count) {
                break;
            }

        }
        return uploadCount;
    }

    /**
     * 清理图片文件。
     * 由 RabbitMQ 消费者同步调用，方法正常结束后才 ACK。
     *这个方法是消费者最终执行删除的地方。只把 count > 1 改为 count > 0
     * 因为当前图片已经软删除，不会被 MyBatis-Plus 的统计 SQL 计入。
     * @param oldPicture 已软删除图片的信息
     */
    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 当前图片已经软删除，因此这里只统计其他仍然有效的图片记录
        Long count = pictureRepository.lambdaQuery()
                .eq(Picture::getUrl, oldPicture.getUrl())
                .count();

        if (count > 0) {
            // 仍有其他有效图片使用这个文件，不删除 COS 文件
            return;
        }

        // 删除原图
        String url = oldPicture.getUrl();
        cosManager.deleteObject(url);

        /**
         * 删除压缩图
         *
         * String[] split = url.split("\\.");
         * StringBuilder sb = new StringBuilder();
         * for (int i = 0; i < split.length - 1; i++) {
         *     sb.append(split[i]).append(".");
         * }
         * sb.append("webp");
         * String compressUrl = sb.toString();
         * cosManager.deleteObject(compressUrl);
         */

        // 删除缩略图
        cosManager.deleteObject(oldPicture.getThumbnailUrl());
    }

    /**
     * 校验空间的图片权限
     *
     * @param loginUser 登录用户实体类
     * @param picture   图片实体类
     */
    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        Long loginUserId = loginUser.getId();
        if (spaceId == null) {
            //公共图片,无需校验权限,仅本人或管理员可操作
            if (!loginUserId.equals(picture.getUserId()) && !loginUser.isAdmin()) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            //私有空间的图片,仅空间管理员可以操作
            if (!picture.getUserId().equals(loginUserId) ) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    /**
     * 判断有效图片是否已经存在相同来源
     */
    private boolean existsActivePictureBySourceHash(String sourceHash) {
        if (StrUtil.isBlank(sourceHash)) {
            return false;
        }
        return pictureRepository.lambdaQuery()
                .eq(Picture::getSourceHash, sourceHash)
                .eq(Picture::getIsDelete, 0)
                .count() > 0;
    }

    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NO_AUTH_ERROR);

        // 查询当前仍然有效的图片
        Picture oldPicture = pictureRepository.getById(pictureId);

        ThrowUtils.throwIf(BeanUtil.isEmpty(oldPicture), ErrorCode.NOT_FOUND_ERROR);

        /*
         * 在同一个事务中完成：
         * 1. 软删除图片；
         * 2. 创建待发送清理任务；
         * 3. 恢复空间额度。
         *
         * 任意一步失败，整个事务都会回滚。
         */
        Long taskId = transactionTemplate.execute(status -> {
            UpdateWrapper<Picture> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", pictureId)
                    .eq("isDelete", 0)
                    .set("sourceHash", null)
                    .set("isDelete", 1);

            boolean pictureUpdated = pictureRepository.update(updateWrapper);

            ThrowUtils.throwIf(!pictureUpdated, ErrorCode.OPERATION_ERROR, "删除图片失败");

            /*
             * 必须放在 spaceId 判断之前。
             * 公共图片虽然不需要恢复空间额度，
             * 但同样需要创建文件清理任务。
             */
            Long cleanupTaskId = pictureCleanupTaskService.createPendingTask(oldPicture, loginUser);

            Long spaceId = oldPicture.getSpaceId();
            if (spaceId != null) {
                boolean spaceUpdated = spaceApplicationService.lambdaUpdate()
                                .eq(Space::getId, spaceId)
                                .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                                .setSql("totalCount = totalCount - 1")
                                .update();
                ThrowUtils.throwIf(!spaceUpdated, ErrorCode.OPERATION_ERROR, "恢复空间额度失败");
            }

            return cleanupTaskId;
        });

        ThrowUtils.throwIf(taskId == null, ErrorCode.OPERATION_ERROR, "创建图片清理任务失败");

        /*
         * TransactionTemplate.execute 已经返回，
         * 说明数据库事务已经提交。
         *
         * 此处只把任务提交给后台线程，不等待 RabbitMQ Confirm，
         * 更不会等待 COS 删除。
         */
        try {
            /*
             * 异步发布清理任务。
             * 不等待 RabbitMQ Confirm，
             * 更不会等待 COS 删除。
             */
            pictureCleanupPublisher.publishAsync(taskId);
        } catch (TaskRejectedException exception) {
            /*
             * 线程池繁忙时，任务仍保持 STATUS_PENDING。
             * 后续补偿扫描会重新发送，不影响删除接口返回。
             */
            log.warn("图片清理发布线程池繁忙，任务等待补偿发送，taskId: {}", taskId);
        }
    }

    @Override
    public BaseResponse<Boolean> editPicture(Picture picture, User loginUser) {

        //将标签List转换为 JSON
        picture.setTags(JSONUtil.toJsonStr(picture.getTags()));
        //设置编辑时间
        picture.setEditTime(new Date());
        //数据校验
        picture.validatePicture();

        //判断图片是否存在
        Long id = picture.getId();
        Picture oldPicture = pictureRepository.getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(oldPicture), ErrorCode.NOT_FOUND_ERROR);

        //校验权限//改为使用注解鉴权
        //checkPictureAuth(loginUser, oldPicture);
        //填充审核参数
        fillReviewParams(picture, loginUser);
        //操作数据库
        boolean result = pictureRepository.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    private String getSourceHash(String fileUrl) {
        if (StrUtil.isBlank(fileUrl)) {
            return null;
        }
        return DigestUtil.sha256Hex(fileUrl.trim());
    }
    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限,用户只能查自己空间下的图片
        Space space = spaceApplicationService.getById(spaceId);
        ThrowUtils.throwIf(BeanUtil.isEmpty(space), ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if(!space.getUserId().equals(loginUser.getId())){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有权限访问");
        }
        //3.查询该空间下所有图片(必须要有主色调)
        List<Picture> pictureList = pictureRepository.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        //如果没有图片返回空列表
        if(CollectionUtil.isEmpty(pictureList)){
            return Collections.emptyList();
        }

        //将颜色字符串转换为主色调对象
        Color targetColor=Color.decode(picColor);
        //4.根据主色调查询图片
        List<Picture> sortedPictureList = pictureList.stream()
                .sorted(Comparator.comparingDouble(
                        picture -> {
                            String hexColor = picture.getPicColor();
                            if (StrUtil.isBlank(hexColor)) {
                                //如果图片没有主色调,则返回最大值,确保被最后排序
                                return Double.MAX_VALUE;
                            }
                            Color pictureColor = Color.decode(hexColor);
                            return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                        }))
                .limit(12)
                .toList();
        //5.返回结果
        return sortedPictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
    }

    /**
     * 批量编辑图片分类和标签和重命名
     * @param pictureEditByBatchRequest 图片编辑批量请求参数
     * @param loginUser 登录用户对象
     */
    @Transactional
    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        //1.校验参数
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        //要编辑的分类和标签
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();

        ThrowUtils.throwIf(CollectionUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(BeanUtil.isEmpty(loginUser), ErrorCode.NO_AUTH_ERROR);
        //2.校验权限
        Space space = spaceApplicationService.getById(spaceId);
        ThrowUtils.throwIf(BeanUtil.isEmpty(space), ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if(!space.getUserId().equals(loginUser.getId())){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有权限访问");
        }
        //3.查询指定图片(仅选择需要字段)
        List<Picture> pictureList = pictureRepository.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();
        if(CollectionUtil.isEmpty(pictureList)){
            return;
        }
        //4.更新分类和标签
        pictureList.forEach(picture -> {
            if(StrUtil.isNotBlank(category)){
                picture.setCategory(category);
            }
            if(CollectionUtil.isNotEmpty(tags)){
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        //批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
       fillPictureWithNameRule(pictureList, nameRule);
        boolean result = pictureRepository.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR,"批量编辑图片分类和标签失败");
    }



    /**
     * nameRule 格式：图片{序号}
     *
     * @param pictureList 图片列表
     * @param nameRule 图片名称规则
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if(StrUtil.isBlank(nameRule)||CollectionUtil.isEmpty(pictureList)){
            return;
        }
        long count=1;

        try {
            for(Picture picture:pictureList){
                String pictureName=nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误",e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }

    }
    /**
     * 创建图片括图任务
     * @param createPictureOutPaintingTaskRequest 创建图片括图任务请求参数
     * @param loginUser 登录用户对象
     * @return 创建图片括图任务响应参数
     */
    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest , User loginUser) {
        //获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = Optional.ofNullable(pictureRepository.getById(pictureId)).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"));
        //校验权限//改为使用注解鉴权
        //checkPictureAuth(loginUser, picture);
        //创建括图任务
        CreateOutPaintingTaskRequest taskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());//为输入ai扩图里增加图片url
        taskRequest.setInput(input);
        taskRequest.setParameters(createPictureOutPaintingTaskRequest.getParameters());
        //创建任务,并根据任务返回url
        return aliYunAiApi.createOutPaintingTask(taskRequest);

    }
}




