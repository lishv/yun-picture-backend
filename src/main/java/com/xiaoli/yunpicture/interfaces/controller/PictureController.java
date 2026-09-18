package com.xiaoli.yunpicture.interfaces.controller;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xiaoli.yunpicture.application.service.SpaceApplicationService;
import com.xiaoli.yunpicture.domain.space.valueobject.SpaceTypeEnum;
import com.xiaoli.yunpicture.infrastructure.annotation.AuthCheck;
import com.xiaoli.yunpicture.infrastructure.api.aliyunai.AliYunAiApi;
import com.xiaoli.yunpicture.infrastructure.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.xiaoli.yunpicture.infrastructure.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.xiaoli.yunpicture.infrastructure.api.imagesearch.ImageSearchApiFacade;
import com.xiaoli.yunpicture.infrastructure.api.imagesearch.model.ImageSearchResult;
import com.xiaoli.yunpicture.infrastructure.common.BaseResponse;
import com.xiaoli.yunpicture.infrastructure.common.DeleteRequest;
import com.xiaoli.yunpicture.infrastructure.common.ResultUtils;
import com.xiaoli.yunpicture.domain.user.constant.UserConstant;
import com.xiaoli.yunpicture.infrastructure.exception.BusinessException;
import com.xiaoli.yunpicture.infrastructure.exception.ErrorCode;
import com.xiaoli.yunpicture.infrastructure.exception.ThrowUtils;
import com.xiaoli.yunpicture.interfaces.assembler.PictureAssembler;
import com.xiaoli.yunpicture.interfaces.dto.picture.*;
import com.xiaoli.yunpicture.shared.auth.SpaceUserAuthManager;
import com.xiaoli.yunpicture.shared.auth.StpKit;
import com.xiaoli.yunpicture.shared.auth.annotation.SaSpaceCheckPermission;
import com.xiaoli.yunpicture.shared.auth.model.SpaceUserPermissionConstant;
import com.xiaoli.yunpicture.domain.picture.entity.Picture;
import com.xiaoli.yunpicture.domain.space.entity.Space;
import com.xiaoli.yunpicture.domain.user.entity.User;
import com.xiaoli.yunpicture.domain.picture.valueobject.PictureReviewStatusEnmu;
import com.xiaoli.yunpicture.interfaces.vo.picture.CursorPageVO;
import com.xiaoli.yunpicture.interfaces.vo.picture.PictureTagCategory;
import com.xiaoli.yunpicture.interfaces.vo.picture.PictureVO;
import com.xiaoli.yunpicture.application.service.PictureApplicationService;
import com.xiaoli.yunpicture.application.service.UserApplicationService;
import com.xiaoli.yunpicture.shared.websocket.PictureEditHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/picture")
public class PictureController {
    @Resource
    private PictureApplicationService pictureApplicationService;
    @Resource
    private UserApplicationService userApplicationService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private AliYunAiApi aliYunApi;
    @Resource
    private SpaceApplicationService spaceApplicationService;
    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;
    @Resource
    private PictureEditHandler pictureEditHandler;
    /**
     * 本地缓存
     */
    private final Cache<String, String> LOCAL_CACHE =Caffeine.newBuilder()
            .initialCapacity(1024)// 初始容量 1024条目
            .maximumSize(10000L)// 最大容量 10000条目
            .expireAfterWrite(5L, TimeUnit.MINUTES)// 缓存 5 分钟移除
            .build();


    /**
     * 上传文件
     * @param multipartFile 文件
     * @return 文件路径
     */
    @PostMapping("/upload")
    @SaSpaceCheckPermission(value= SpaceUserPermissionConstant.PICTURE_UPLOAD)
   // @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPicture(@RequestPart("file") MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, HttpServletRequest request) {
        User loginUser = userApplicationService.getLoginUser(request);
        PictureVO pictureVO = pictureApplicationService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }
    /**
     * 通过Url上传文件
     * @param pictureUploadRequest 上传请求参数
     * @return 文件路径
     */
    @PostMapping("/upload/url")
    @SaSpaceCheckPermission(value= SpaceUserPermissionConstant.PICTURE_UPLOAD)
    // @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        String fileUrl = pictureUploadRequest.getFileUrl();
        User loginUser = userApplicationService.getLoginUser(request);
        PictureVO pictureVO = pictureApplicationService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 删除图片
     * @param deleteRequest 删除请求参数
     * @param request HTTP请求对象
     * @return 删除结果
     */
    @PostMapping("/delete")
    @SaSpaceCheckPermission(value= SpaceUserPermissionConstant.PICTURE_DELETE)
    public BaseResponse<Boolean>deletePicture(@RequestBody DeleteRequest  deleteRequest,HttpServletRequest request) {
        if(BeanUtil.isEmpty(deleteRequest)||deleteRequest.getId()<=0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userApplicationService.getLoginUser(request);
        pictureApplicationService.deletePicture(deleteRequest.getId(),loginUser);

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
    public BaseResponse<Boolean>updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        if(BeanUtil.isEmpty(pictureUpdateRequest)||pictureUpdateRequest.getId()<=0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Picture picture = PictureAssembler.toPictureEntity(pictureUpdateRequest);
        //将标签List转换为 JSON
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));

        //数据校验
        pictureApplicationService.validatePicture(picture);
        long id = picture.getId();
        Picture oldPicture = pictureApplicationService.getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(oldPicture),ErrorCode.NOT_FOUND_ERROR);
        //填充审核参数
        User loginUser = userApplicationService.getLoginUser(request);
        pictureApplicationService.fillReviewParams(picture, loginUser);
        //操作数据库
        boolean result = pictureApplicationService.updateById(picture);
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
        Picture picture = pictureApplicationService.getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(picture),ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(picture);
    }

    /**
     * 根据id获取团队空间的图片信息(用户,封装类)
     * @param id 图片id
     * @param request HTTP请求对象
     * @return 图片VO信息
     */
    @GetMapping("/get/vo")
  //  @SaSpaceCheckPermission(value= SpaceUserPermissionConstant.PICTURE_VIEW)
    public BaseResponse<PictureVO>getPictureVOById(@RequestParam("id") Long id,HttpServletRequest request) {
        ThrowUtils.throwIf(id<=0,ErrorCode.PARAMS_ERROR);
        Picture picture = pictureApplicationService.getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(picture),ErrorCode.NOT_FOUND_ERROR);
        //空间权限校验
        Long spaceId = picture.getSpaceId();
        //仅用户自己/管理员才能看到非公共图片
        Space space=null;
        if(spaceId!=null) {
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission,ErrorCode.NO_AUTH_ERROR);
            // User loginUser = userService.getLoginUser(request);
            //改为使用注解鉴权
            //pictureService.checkPictureAuth(loginUser,picture);
            space = spaceApplicationService.getById(spaceId);
            ThrowUtils.throwIf(BeanUtil.isEmpty(space),ErrorCode.NOT_FOUND_ERROR,"空间不存在");
        }
        //获取权限列表
        User loginUser = userApplicationService.getLoginUser(request);
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
        //获取封装类
        PictureVO pictureVO = pictureApplicationService.getPictureVO(picture, request);
        pictureVO.setPermissionList(permissionList);
        return ResultUtils.success(pictureVO);
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
        Page<Picture> picturePage = pictureApplicationService.page(new Page<>(current, pageSize), pictureApplicationService.getQueryWrapper(pictureQueryRequest));
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
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission,ErrorCode.NO_AUTH_ERROR,"没有空间权限");
            //已改为使用注解鉴权
           /* User loginUser = userService.getLoginUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(BeanUtil.isEmpty(space),ErrorCode.NOT_FOUND_ERROR,"空间不存在");
            if(!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"没有空间权限");
            }*/
        }
        //查询数据库
        Page<Picture> picturePage = pictureApplicationService.page(new Page<>(current, pageSize), pictureApplicationService.getQueryWrapper(pictureQueryRequest));
        //返回图片封装类列表
        return  ResultUtils.success(pictureApplicationService.getPictureVOPage(picturePage, request));
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
            User loginUser = userApplicationService.getLoginUser(request);
            Space space = spaceApplicationService.getById(spaceId);
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
        Page<Picture> picturePage = pictureApplicationService.page(new Page<>(current, pageSize), pictureApplicationService.getQueryWrapper(pictureQueryRequest));
        Page<PictureVO> pictureVOPage = pictureApplicationService.getPictureVOPage(picturePage, request);
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


        Long spaceId = cursorQueryRequest.getSpaceId();
        if(spaceId==null) {
            //无空间ID,只能看到审核通过的图片公共图片
            cursorQueryRequest.setNullSpaceId(true);//设置为true,查询公共图片
            cursorQueryRequest.setReviewStatus(PictureReviewStatusEnmu.PASS.getValue());
        }
        else{
            //私有空间,校验权限,目前私有空间不使用游标查询,但作为安全考虑,仍校验权限空间id,防止越权
            User loginUser = userApplicationService.getLoginUser(request);
            Space space = spaceApplicationService.getById(spaceId);
            ThrowUtils.throwIf(BeanUtil.isEmpty(space),ErrorCode.NOT_FOUND_ERROR,"空间不存在");
            if(!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"没有空间权限");
            }
        }
        //接入缓存,如果有缓存,直接返回
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
        //缓存未命中限流, Redis 未命中后，先限制缓存未命中的数据库查询次数,同一个 IP 1 秒内最多允许 5 次缓存未命中查询数据库
        String clientIp = request.getRemoteAddr();
        String missRateLimitKey= "yunpicture:picture:cache-miss:ip:" + clientIp;//未命中的缓存key
        Long missCount = stringRedisTemplate.opsForValue().increment(missRateLimitKey);
        if (missCount != null && missCount == 1) {
            //如果missCount为1,则说明是第一次查询,设置过期时间为1秒
            stringRedisTemplate.expire(missRateLimitKey, 1, TimeUnit.SECONDS);
        }
        // 同一个 IP 1 秒内最多允许 5 次缓存未命中查询数据库
        if (missCount != null && missCount > 5) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求过于频繁，请稍后再试");
        }

        //3.未命中,则查询数据库,直接把结果返回,并存入两个缓存,这里空值也缓存进去,防止穿透
        CursorPageVO<PictureVO> cursorPageVO = pictureApplicationService.listPictureVOByCursor(cursorQueryRequest, request);
        //为了减少内存压力,我们将空值只缓存在redis里,设置30s
        if(BeanUtil.isEmpty(cursorPageVO)||cursorPageVO.getRecords()==null||cursorPageVO.getRecords().isEmpty()){
            stringRedisTemplate.opsForValue().set(cacheKey,JSONUtil.toJsonStr(cursorPageVO),30, TimeUnit.SECONDS);
            return ResultUtils.success(cursorPageVO);
        }

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
    @SaSpaceCheckPermission(value= SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest,HttpServletRequest request) {
        if(BeanUtil.isEmpty(pictureEditRequest)||pictureEditRequest.getId()<=0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userApplicationService.getLoginUser(request);
        Picture picture = PictureAssembler.toPictureEntity(pictureEditRequest);
        return pictureApplicationService.editPicture(picture,loginUser);
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
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewRequest == null,ErrorCode.PARAMS_ERROR);
        User loginUser = userApplicationService.getLoginUser(request);
        pictureApplicationService.doPictureReview(pictureReviewRequest, loginUser);
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
    public BaseResponse<Integer> uploadPictureByBatch(@RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null,ErrorCode.PARAMS_ERROR);
        User loginUser = userApplicationService.getLoginUser(request);
        Integer uploadCount = pictureApplicationService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);

    }
    /**
     * 以图搜图
     */
    @PostMapping("/search/picture")
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(@RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest) {
        ThrowUtils.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture oldPicture = pictureApplicationService.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        List<ImageSearchResult> resultList = ImageSearchApiFacade.searchImage(oldPicture.getUrl());
        return ResultUtils.success(resultList);
    }

    /**
     * 以颜色搜图
     * @param searchPictureByColorRequest   搜索图片颜色请求参数
     * @return 搜索结果列表
     */
    @PostMapping("/search/color")
    @SaSpaceCheckPermission(value= SpaceUserPermissionConstant.PICTURE_VIEW)
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchPictureByColorRequest searchPictureByColorRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByColorRequest == null, ErrorCode.PARAMS_ERROR);
        String picColor = searchPictureByColorRequest.getPicColor();
        Long spaceId = searchPictureByColorRequest.getSpaceId();
        User loginUser = userApplicationService.getLoginUser(request);
        List<PictureVO> result = pictureApplicationService.searchPictureByColor(spaceId, picColor, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 批量编辑图片分类和标签
     * @param pictureEditByBatchRequest  批量编辑图片分类和标签请求参数
     * @param request HTTP请求对象
     * @return 操作结果
     */
    @PostMapping("/edit/batch")
    @SaSpaceCheckPermission(value= SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPictureByBatch(@RequestBody PictureEditByBatchRequest pictureEditByBatchRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userApplicationService.getLoginUser(request);
        pictureApplicationService.editPictureByBatch(pictureEditByBatchRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 创建 AI 扩图任务
     * @param createPictureOutPaintingTaskRequest 创建 AI 扩图任务请求参数
     * @param request HTTP请求对象
     * @return 创建 AI 扩图任务响应参数
     */
    @SaSpaceCheckPermission(value= SpaceUserPermissionConstant.PICTURE_EDIT)
    @PostMapping("/out_painting/create_task")
    public BaseResponse<CreateOutPaintingTaskResponse> createPictureOutPaintingTask(
            @RequestBody CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest,
            HttpServletRequest request) {
        if (createPictureOutPaintingTaskRequest == null || createPictureOutPaintingTaskRequest.getPictureId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userApplicationService.getLoginUser(request);
        CreateOutPaintingTaskResponse response = pictureApplicationService.createPictureOutPaintingTask(createPictureOutPaintingTaskRequest, loginUser);
        return ResultUtils.success(response);
    }
    /**
     * 查询 AI 扩图任务
     */
    @GetMapping("/out_painting/get_task")
    public BaseResponse<GetOutPaintingTaskResponse> getPictureOutPaintingTask(String taskId) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR);
        GetOutPaintingTaskResponse task = aliYunApi.getOutPaintingTask(taskId);
        return ResultUtils.success(task);
    }


}
