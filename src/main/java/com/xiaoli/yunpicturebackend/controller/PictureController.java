package com.xiaoli.yunpicturebackend.controller;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import com.xiaoli.yunpicturebackend.model.entity.Space;
import com.xiaoli.yunpicturebackend.model.entity.User;
import com.xiaoli.yunpicturebackend.model.enums.PictureReviewStatusEnmu;
import com.xiaoli.yunpicturebackend.model.vo.CursorPageVO;
import com.xiaoli.yunpicturebackend.model.vo.PictureTagCategory;
import com.xiaoli.yunpicturebackend.model.vo.PictureVO;
import com.xiaoli.yunpicturebackend.service.PictureService;
import com.xiaoli.yunpicturebackend.service.SpaceService;
import com.xiaoli.yunpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/picture")
public class PictureController {
    @Resource
    private PictureService pictureService;
    @Resource
    private UserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 本地缓存
     */
    private final Cache<String, String> LOCAL_CACHE =Caffeine.newBuilder()
            .initialCapacity(1024)// 初始容量 1024条目
            .maximumSize(10000L)// 最大容量 10000条目
            .expireAfterWrite(5L, TimeUnit.MINUTES)// 缓存 5 分钟移除
            .build();
    @Autowired
    private SpaceService spaceService;

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
        pictureService.deletePicture(deleteRequest.getId(),loginUser);

        return ResultUtils.success(true);
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
        //空间权限校验
        Long spaceId = picture.getSpaceId();
        //仅用户自己/管理员才能看到非公共图片
        if(spaceId!=null) {
            User loginUser = userService.getLoginUser(request);
            pictureService.checkPictureAuth(loginUser,picture);
        }

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
        //todo:管理员现在无法看到用户的私有图片 ,查询数据库
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
        Long spaceId = pictureQueryRequest.getSpaceId();
        if(spaceId==null) {
            //仅用户自己/管理员才能看到非公共图片
            pictureQueryRequest.setNullSpaceId(true);//设置为true,查询公共图片
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnmu.PASS.getValue());
        }else{
            //私有空间,校验权限
            User loginUser = userService.getLoginUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(BeanUtil.isEmpty(space),ErrorCode.NOT_FOUND_ERROR,"空间不存在");
            if(!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"没有空间权限");
            }
        }
        //查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize), pictureService.getQueryWrapper(pictureQueryRequest));
        //返回图片封装类列表
        return  ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }

    /**
     * 分页查询图片(用户,封装类,带有缓存)
     * @param pictureQueryRequest 分页查询图片请求参数
     * @param request HTTP请求对象
     * @return 分页查询图片结果列表
     */
    @Deprecated //已废弃,不建议使用,未设置spaceId校验,存在安全风险
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>>listPictureVOByPageWitchCache(@RequestBody PictureQueryRequest pictureQueryRequest,HttpServletRequest request) {
        long pageSize = pictureQueryRequest.getPageSize();
        long current = pictureQueryRequest.getCurrent();
        //限制爬虫
        ThrowUtils.throwIf(pageSize>20,ErrorCode.PARAMS_ERROR);

        //普通用户只能看到审核通过的图片
        Long spaceId = pictureQueryRequest.getSpaceId();
        if(spaceId==null) {
            //仅用户自己/管理员才能看到非公共图片
            pictureQueryRequest.setNullSpaceId(true);//设置为true,查询公共图片
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnmu.PASS.getValue());
        }else{
            //私有空间,校验权限
            User loginUser = userService.getLoginUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(BeanUtil.isEmpty(space),ErrorCode.NOT_FOUND_ERROR,"空间不存在");
            if(!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"没有空间权限");
            }
        }

        //1.构建key
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        //转换成md5
        String hasKey = DigestUtils.md5Hex(queryCondition.getBytes());


        String cacheKey=String.format("yunpicture:listPictureVOByPage:%s", hasKey);
        //2.查询缓存
        //2.1先从本地缓存查询,如果有,直接返回
        String cacheValue = LOCAL_CACHE.getIfPresent(cacheKey);
        if(cacheValue!=null){
            //缓存命中,返回缓存结果
            Page<PictureVO> cachePage = JSONUtil.toBean(cacheValue, Page.class);
            return  ResultUtils.success(cachePage);
        }
        //2.2如果本地缓存未命中,则查询redis缓存
        cacheValue = stringRedisTemplate.opsForValue().get(cacheKey);
        if(cacheValue!=null){
            //缓存命中,更新本地缓存,返回缓存结果
            LOCAL_CACHE.put(cacheKey,cacheValue);
            Page<PictureVO> cachePage = JSONUtil.toBean(cacheValue, Page.class);
            return  ResultUtils.success(cachePage);
        }
        //3.若均未命中,则查询数据库,并存入两个缓存
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize), pictureService.getQueryWrapper(pictureQueryRequest));
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);
        cacheValue= JSONUtil.toJsonStr(pictureVOPage);
        //3.1存入本地缓存
        LOCAL_CACHE.put(cacheKey, cacheValue);
        //3.2存入redis缓存
        //采用缓存过期时间5-10分钟,预防雪崩
        int cacheExpireTime = 300+ RandomUtil.randomInt(0,300);//5-10分钟
        stringRedisTemplate.opsForValue().set(cacheKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);

        //返回图片封装类列表
        return  ResultUtils.success(pictureVOPage);
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
        ThrowUtils.throwIf(cursorQueryRequest == null||cursorQueryRequest.getPageSize()>20, ErrorCode.PARAMS_ERROR);

        //普通用户只能看到审核通过的图片
        Long spaceId = cursorQueryRequest.getSpaceId();
        if(spaceId==null) {
            //仅用户自己/管理员才能看到非公共图片
            cursorQueryRequest.setNullSpaceId(true);//设置为true,查询公共图片
            cursorQueryRequest.setReviewStatus(PictureReviewStatusEnmu.PASS.getValue());
        }else{
            //私有空间,校验权限
            User loginUser = userService.getLoginUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(BeanUtil.isEmpty(space),ErrorCode.NOT_FOUND_ERROR,"空间不存在");
            if(!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"没有空间权限");
            }
        }
        //接入缓存,如果有,直接返回
        //1.构建key
        String queryCondition = JSONUtil.toJsonStr(cursorQueryRequest);
        //转换成md5
        String hasKey = DigestUtils.md5Hex(queryCondition.getBytes());
        String cacheKey=String.format("yunpicture:listPictureVOByCursor:%s", hasKey);
        //2.查询缓存
        //2.1先从本地缓存查询,如果有,直接返回
        String cacheValue=LOCAL_CACHE.getIfPresent(cacheKey);
        if(cacheValue!=null){
            //缓存命中,返回缓存结果
            CursorPageVO<PictureVO> cacheCursorPageVO = JSONUtil.toBean(cacheValue, CursorPageVO.class);
            return ResultUtils.success(cacheCursorPageVO);
        }
        //2.2如果本地缓存未命中,则查询redis缓存
        cacheValue= stringRedisTemplate.opsForValue().get(cacheKey);
        if(cacheValue!=null){
            //缓存命中,更新本地缓存,返回缓存结果
            LOCAL_CACHE.put(cacheKey,cacheValue);
            CursorPageVO<PictureVO> cacheCursorPageVO = JSONUtil.toBean(cacheValue, CursorPageVO.class);
            return ResultUtils.success(cacheCursorPageVO);
        }
        //3.未命中,则查询数据库
        CursorPageVO<PictureVO> cursorPageVO = pictureService.listPictureVOByCursor(cursorQueryRequest, request);
        //3.1存入本地缓存
        LOCAL_CACHE.put(cacheKey, JSONUtil.toJsonStr(cursorPageVO));
        //3.2存入redis缓存
        int cacheExpireTime = 300+ RandomUtil.randomInt(0,300);//5-10分钟
        stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(cursorPageVO), cacheExpireTime, TimeUnit.SECONDS);
        return ResultUtils.success(cursorPageVO);
    }

    /**
     * 编辑图片信息(仅本人/管理员可用)
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest,HttpServletRequest request) {
        if(BeanUtil.isEmpty(pictureEditRequest)||pictureEditRequest.getId()<=0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);

        return pictureService.editPicture(pictureEditRequest,loginUser);
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
