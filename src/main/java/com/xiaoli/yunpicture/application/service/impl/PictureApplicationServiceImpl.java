package com.xiaoli.yunpicture.application.service.impl;

import java.util.List;


import cn.hutool.core.collection.CollUtil;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoli.yunpicture.application.service.PictureApplicationService;
import com.xiaoli.yunpicture.application.service.SpaceApplicationService;
import com.xiaoli.yunpicture.application.service.UserApplicationService;
import com.xiaoli.yunpicture.domain.picture.service.PictureDomainService;

import com.xiaoli.yunpicture.domain.space.entity.Space;
import com.xiaoli.yunpicture.domain.space.valueobject.SpaceTypeEnum;
import com.xiaoli.yunpicture.infrastructure.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.xiaoli.yunpicture.infrastructure.common.BaseResponse;

import com.xiaoli.yunpicture.infrastructure.exception.BusinessException;
import com.xiaoli.yunpicture.infrastructure.exception.ErrorCode;

import com.xiaoli.yunpicture.infrastructure.exception.ThrowUtils;
import com.xiaoli.yunpicture.interfaces.dto.picture.*;

import com.xiaoli.yunpicture.domain.picture.entity.Picture;

import com.xiaoli.yunpicture.domain.user.entity.User;

import com.xiaoli.yunpicture.interfaces.vo.user.UserVO;
import com.xiaoli.yunpicture.interfaces.vo.picture.CursorPageVO;
import com.xiaoli.yunpicture.interfaces.vo.picture.PictureVO;

import com.xiaoli.yunpicture.infrastructure.mapper.PictureMapper;

import com.xiaoli.yunpicture.shared.websocket.PictureEditHandler;
import lombok.extern.slf4j.Slf4j;

import net.bytebuddy.implementation.bytecode.Throw;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


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
public class PictureApplicationServiceImpl extends ServiceImpl<PictureMapper, Picture> implements PictureApplicationService {
    @Resource
    private UserApplicationService userApplicationService;
    @Resource
    private PictureDomainService pictureDomainService;
    @Resource
    private SpaceApplicationService spaceApplicationService;
    @Resource
    private PictureEditHandler pictureEditHandler;

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
        PictureVO pictureVO = pictureDomainService.uploadPicture(inputSource, pictureUploadRequest, loginUser);
        // 2. 用例编排：仅在“更新已有图片”且属于“团队空间”时，触发协同编辑完成广播
        if (pictureUploadRequest != null && pictureUploadRequest.getId() != null) {
            Long spaceId = pictureVO.getSpaceId(); // 从落库返回的 VO 获取最准确的 spaceId（无论是请求传入还是老图回填）
            // 3.仅在“更新已有图片”且属于“团队空间”时，触发协同编辑完成广播
            if (spaceId != null) {
                Space space = spaceApplicationService.getById(spaceId);
                if (space != null && SpaceTypeEnum.TEAM.getValue().equals(space.getSpaceType())) {
                    pictureEditHandler.notifyPictureEditComplete(pictureUploadRequest.getId(), loginUser);
                }
            }
        }
        return pictureVO;
    }

    /**
     * 获取查询条件对象
     *
     * @param pictureQueryRequest 查询参数
     * @return 查询条件对象
     */
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        return pictureDomainService.getQueryWrapper(pictureQueryRequest);
    }


    /**
     * 获取图片包装类(单条)
     *
     * @param picture 图片实体类
     * @param request HTTP请求对象
     * @return 图片VO类
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        //对象转换成封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userApplicationService.getUserById(userId);
            UserVO userVO = userApplicationService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    /**
     * PictureApplicationService
     * 获取图片包装类(多条)
     * @param picturePage 分页对象
     * @param request     HTTP请求对象
     * @return 分页对象,包含图片VO类列表
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
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

        List<User> userList = userApplicationService.listByIds(userIdSet);

        Map<Long, List<User>> userIdUserListMap = userList.stream()
                .collect(Collectors.groupingBy(User::getId));

        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;

            if (userId != null && userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }

            pictureVO.setUser(userApplicationService.getUserVO(user));
        });

        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    /**
     * 校验图片数据是否符合要求
     *
     * @param picture 图片实体类
     */
    @Override
    public void validatePicture(Picture picture) {
        if (picture == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        picture.validatePicture();
    }


    /**
     * 使用复合游标查询图片列表。
     *
     * @param cursorRequest 游标查询参数
     * @param request       HTTP 请求
     * @return 游标查询结果
     */
    @Override
    public CursorPageVO<PictureVO> listPictureVOByCursor(PictureCursorQueryRequest cursorRequest, HttpServletRequest request) {
        int pageSize = cursorRequest.getPageSize() == null ? 20 : cursorRequest.getPageSize();
        ThrowUtils.throwIf(pageSize <= 0 || pageSize > 20, ErrorCode.PARAMS_ERROR, "每次加载数量必须在 1 到 20 之间");
        boolean hasCursorCreateTime = cursorRequest.getCursorCreateTime() != null;
        boolean hasCursorId = cursorRequest.getCursorId() != null;

        // createTime 和 id 必须同时存在或同时不存在。
        if (hasCursorCreateTime != hasCursorId) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 复用已有的图片搜索条件。
        PictureQueryRequest queryRequest = new PictureQueryRequest();
        queryRequest.setSearchText(cursorRequest.getSearchText());
        queryRequest.setCategory(cursorRequest.getCategory());
        queryRequest.setTags(cursorRequest.getTags());
        queryRequest.setSpaceId(cursorRequest.getSpaceId());
        queryRequest.setNullSpaceId(cursorRequest.getNullSpaceId());
        queryRequest.setReviewStatus(cursorRequest.getReviewStatus());

        //根据条件查询图片
        QueryWrapper<Picture> queryWrapper = getQueryWrapper(queryRequest);

        //在上一批图片的基础条件上查询下一批图片
        // 下一批数据：
        // createTime 小于上一批最后一项，
        // 或 createTime 相同但 id 更小。
        if (hasCursorCreateTime) {
            queryWrapper.and(
                    wrapper -> wrapper.lt("createTime", cursorRequest.getCursorCreateTime()).or(nested -> nested.eq("createTime", cursorRequest.getCursorCreateTime()).lt("id", cursorRequest.getCursorId()))
            );
        }

        queryWrapper
                .orderByDesc("createTime")
                .orderByDesc("id")
                // 多查询一项，用于判断还有没有下一批。
                .last("LIMIT " + (pageSize + 1));
        //查询这一批图片数据
        List<Picture> queriedPictures = this.list(queryWrapper);

        boolean hasMore = queriedPictures.size() > pageSize;//因为多查询了一项，所以可以据此判断是否有下一批

        List<Picture> currentPictures;

        if (hasMore) {
            //截取前 pageSize 项的数据
            currentPictures = new ArrayList<>(queriedPictures.subList(0, pageSize));
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
        //如果有下一批数据，设置下一批游标,用于下一次查询时的条件
        if (hasMore && !currentPictures.isEmpty()) {
            Picture lastPicture = currentPictures.get(currentPictures.size() - 1);
            result.setNextCursorCreateTime(lastPicture.getCreateTime());
            result.setNextCursorId(lastPicture.getId());
        }

        return result;
    }

    /**
     * 图片审核
     *
     * @param pictureReviewRequest 审核图片请求参数
     * @param loginUser            登录用户实体
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        pictureDomainService.doPictureReview(pictureReviewRequest, loginUser);
    }

    /**
     * 填充审核参数
     *
     * @param picture   图片实体
     * @param loginUser 登录用户实体
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        pictureDomainService.fillReviewParams(picture, loginUser);
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
        return pictureDomainService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
    }

    /**
     * 异步删除图片文件
     *
     * @param oldPicture 图片实体类
     */
    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        pictureDomainService.clearPictureFile(oldPicture);
    }

    /**
     * 校验空间的图片权限
     *
     * @param loginUser 登录用户实体类
     * @param picture   图片实体类
     */
    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        pictureDomainService.checkPictureAuth(loginUser, picture);
    }


    @Override
    public void deletePicture(long pictureId, User loginUser) {
        pictureDomainService.deletePicture(pictureId, loginUser);
    }

    @Override
    public BaseResponse<Boolean> editPicture(Picture picture, User loginUser) {
        return pictureDomainService.editPicture(picture, loginUser);
    }


    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        return pictureDomainService.searchPictureByColor(spaceId, picColor, loginUser);
    }

    /**
     * 批量编辑图片分类和标签和重命名
     *
     * @param pictureEditByBatchRequest 图片编辑批量请求参数
     * @param loginUser                 登录用户对象
     */
    @Transactional
    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        pictureDomainService.editPictureByBatch(pictureEditByBatchRequest, loginUser);
    }

    /**
     * 创建图片括图任务
     *
     * @param createPictureOutPaintingTaskRequest 创建图片括图任务请求参数
     * @param loginUser                           登录用户对象
     * @return 创建图片括图任务响应参数
     */
    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        return pictureDomainService.createPictureOutPaintingTask(createPictureOutPaintingTaskRequest, loginUser);
    }
}




