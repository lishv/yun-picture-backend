package com.xiaoli.yunpicturebackend.controller;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoli.yunpicturebackend.annotation.AuthCheck;
import com.xiaoli.yunpicturebackend.common.BaseResponse;
import com.xiaoli.yunpicturebackend.common.DeleteRequest;
import com.xiaoli.yunpicturebackend.common.ResultUtils;
import com.xiaoli.yunpicturebackend.constant.UserConstant;
import com.xiaoli.yunpicturebackend.exception.BusinessException;
import com.xiaoli.yunpicturebackend.exception.ErrorCode;
import com.xiaoli.yunpicturebackend.exception.ThrowUtils;
import com.xiaoli.yunpicturebackend.model.dto.picture.*;
import com.xiaoli.yunpicturebackend.model.entity.Picture;
import com.xiaoli.yunpicturebackend.model.entity.User;
import com.xiaoli.yunpicturebackend.model.enums.PictureReviewStatusEnmu;
import com.xiaoli.yunpicturebackend.model.vo.CursorPageVO;
import com.xiaoli.yunpicturebackend.model.vo.PictureTagCategory;
import com.xiaoli.yunpicturebackend.model.vo.PictureVO;
import com.xiaoli.yunpicturebackend.service.PictureService;
import com.xiaoli.yunpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/picture")
public class PictureController {
    @Resource
    private PictureService pictureService;
    @Resource
    private UserService userService;
    /**
     * 上传文件
     * @param multipartFile 文件
     * @return 文件路径
     */
    @PostMapping("/upload")
   // @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }
    /**
     * 通过Url上传文件
     * @param pictureUploadRequest 上传请求参数
     * @return 文件路径
     */
    @PostMapping("/upload/url")
    // @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        String fileUrl = pictureUploadRequest.getFileUrl();
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 删除图片
     * @param deleteRequest 删除请求参数
     * @param request HTTP请求对象
     * @return 删除结果
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean>deletePicture(@RequestBody DeleteRequest  deleteRequest,HttpServletRequest request) {
        if(BeanUtil.isEmpty(deleteRequest)||deleteRequest.getId()<=0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        Long id = deleteRequest.getId();
        //判断数据库是否存在
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(oldPicture),ErrorCode.NOT_FOUND_ERROR);
        //仅本人或者管理员可以删除,即如果这个图片不是本人上传的或者不是身份管理员,则不可以删除
        if(!oldPicture.getUserId().equals(loginUser.getId())&&!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        //软删除,删除操作数据库
        //如果图片来源地址哈希不为空,则删除图片来源地址哈希
        UpdateWrapper<Picture> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", id)
                .eq("isDelete", 0)
                .set("sourceHash", null)
                .set("isDelete", 1);
        boolean result = pictureService.update(updateWrapper);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(result);
    }

    /**
     * 更新图片信息(仅管理员可用)
     * @param pictureUpdateRequest 更新图片请求参数
     * @param request HTTP请求对象
     * @return 更新结果
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean>updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest,HttpServletRequest request) {
        if(BeanUtil.isEmpty(pictureUpdateRequest)||pictureUpdateRequest.getId()<=0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Picture picture = BeanUtil.copyProperties(pictureUpdateRequest, Picture.class);
        //将标签List转换为 JSON
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));

        //数据校验
        pictureService.validatePicture(picture);
        long id = picture.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(oldPicture),ErrorCode.NOT_FOUND_ERROR);
        //填充审核参数
        User loginUser = userService.getLoginUser(request);
        pictureService.fillReviewParams(picture, loginUser);
        //操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据id获取图片信息(仅管理员可用)
     * @param id 图片id
     * @param request HTTP请求对象
     * @return 图片信息
     */
    @GetMapping("/id")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture>getPictureById(@RequestParam("id") Long id,HttpServletRequest request) {
        ThrowUtils.throwIf(id<=0,ErrorCode.PARAMS_ERROR);
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(picture),ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(picture);
    }

    /**
     * 根据id获取图片信息(用户,封装类)
     * @param id 图片id
     * @param request HTTP请求对象
     * @return 图片VO信息
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO>getPictureVOById(@RequestParam("id") Long id,HttpServletRequest request) {
        ThrowUtils.throwIf(id<=0,ErrorCode.PARAMS_ERROR);
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(picture),ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(pictureService.getPictureVO(picture, request));
    }

    /**
     * 分页查询图片(仅管理员可用)
     * @param pictureQueryRequest 分页查询图片请求参数
     * @return 分页查询图片结果
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>>listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long pageSize = pictureQueryRequest.getPageSize();
        long current = pictureQueryRequest.getCurrent();
        //查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize), pictureService.getQueryWrapper(pictureQueryRequest));
        return  ResultUtils.success(picturePage);

    }

    /**
     * 分页查询图片(用户,封装类)
     * @param pictureQueryRequest 分页查询图片请求参数
     * @param request HTTP请求对象
     * @return 分页查询图片结果列表
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>>listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,HttpServletRequest request) {
        long pageSize = pictureQueryRequest.getPageSize();
        long current = pictureQueryRequest.getCurrent();
        //限制爬虫
        ThrowUtils.throwIf(pageSize>20,ErrorCode.PARAMS_ERROR);
        //普通用户只能看到审核通过的图片
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnmu.PASS.getValue());
        //查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize), pictureService.getQueryWrapper(pictureQueryRequest));
        //返回图片封装类列表
        return  ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }

    /**
     * 编辑图片信息(仅本人/管理员可用)
     * @param pictureEditRequest 编辑图片请求参数
     * @param request HTTP请求对象
     * @return 编辑结果
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest,HttpServletRequest request) {
        if(BeanUtil.isEmpty(pictureEditRequest)||pictureEditRequest.getId()<=0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Picture picture = BeanUtil.copyProperties(pictureEditRequest, Picture.class);
        //将标签List转换为 JSON
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        //设置编辑时间
        picture.setEditTime(new Date());
        //数据校验
        pictureService.validatePicture(picture);
        User loginUser = userService.getLoginUser(request);
        //判断图片是否存在
        Long id = picture.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(oldPicture),ErrorCode.NOT_FOUND_ERROR);

        //仅本人/管理员可以编辑
        if(!oldPicture.getUserId().equals(loginUser.getId())&&!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        //填充审核参数
        pictureService.fillReviewParams(picture, loginUser);
        //操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 获取图片标签分类
     * @return 图片标签分类信息
     */
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();

        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报","壁纸");

        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }
    /**
     * 使用游标查询公开图片列表。
     *
     * @param cursorQueryRequest 查询条件和游标
     * @param request HTTP 请求
     * @return 游标查询结果
     */
    @PostMapping("/list/cursor/vo")
    public BaseResponse<CursorPageVO<PictureVO>> listPictureVOByCursor(@RequestBody PictureCursorQueryRequest cursorQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(cursorQueryRequest == null, ErrorCode.PARAMS_ERROR);
        //普通用户只能看到审核通过的图片
        cursorQueryRequest.setReviewStatus(PictureReviewStatusEnmu.PASS.getValue());
        return ResultUtils.success(pictureService.listPictureVOByCursor(cursorQueryRequest, request));
    }

    /**
     * 审核图片仅管理员
     * @param pictureReviewRequest  审核图片请求参数
     * @param request HTTP请求对象
     * @return 审核结果
     */
    @AuthCheck(mustRole =UserConstant.ADMIN_ROLE)
    @PostMapping("/review")
    public BaseResponse<Boolean> doPictureReview( @RequestBody PictureReviewRequest pictureReviewRequest,HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewRequest == null,ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);

    }
    /**
     * 批量抓取图片
     * @param pictureUploadByBatchRequest  批量抓取图片请求参数
     * @param request HTTP请求对象
     * @return 审核结果
     */
    @AuthCheck(mustRole =UserConstant.ADMIN_ROLE)
    @PostMapping("/upload/batch")
    public BaseResponse<Integer> uploadPictureByBatch( @RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,HttpServletRequest request) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null,ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Integer uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);

    }

}
