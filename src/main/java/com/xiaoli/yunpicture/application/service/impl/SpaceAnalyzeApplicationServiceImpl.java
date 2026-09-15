package com.xiaoli.yunpicture.application.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoli.yunpicture.application.service.*;
import com.xiaoli.yunpicture.infrastructure.exception.BusinessException;
import com.xiaoli.yunpicture.infrastructure.exception.ErrorCode;
import com.xiaoli.yunpicture.infrastructure.exception.ThrowUtils;
import com.xiaoli.yunpicture.infrastructure.mapper.SpaceMapper;
import com.xiaoli.yunpicture.interfaces.dto.space.analyze.*;
import com.xiaoli.yunpicture.domain.picture.entity.Picture;
import com.xiaoli.yunpicture.domain.space.entity.Space;
import com.xiaoli.yunpicture.domain.user.entity.User;
import com.xiaoli.yunpicture.interfaces.vo.space.analyze.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author LiShuwen
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2026-08-06 23:05:07
 */
@Slf4j
@Service
public class SpaceAnalyzeApplicationServiceImpl  implements SpaceAnalyzeApplicationService {
    @Resource
    private SpaceApplicationService spaceApplicationService;
    @Resource
    private PictureApplicationService pictureApplicationService;

    /**
     * 获取空间使用分析
     * @param spaceUsageAnalyzeRequest 空间使用分析请求参数
     * @param loginUser 登录用户实体类
     * @return SpaceUsageAnalyzeResponse 分析结果空间使用分析响应参数
     */

    @Override
    public SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser) {
        //校验参数
        //全空间或公共图库,需要从Picture表查询,仅管理员权限
        if (spaceUsageAnalyzeRequest.isQueryAll() || spaceUsageAnalyzeRequest.isQueryPublic()) {
            //校验空间分析权限
            checkSpaceAnalyzeAuth(spaceUsageAnalyzeRequest, loginUser);
            //统计空间的使用空间
            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
            queryWrapper.select("picSize");
            //补充查询范围
            fillAnalyzeQueryWrapper(spaceUsageAnalyzeRequest, queryWrapper);
            //通过只查询字段,统计空间的使用空间,节省查询数据字段,提高查询效率
            List<Object> pictureObjList =  pictureApplicationService.getBaseMapper().selectObjs(queryWrapper);
            Long usedSize = pictureObjList.stream().mapToLong(obj->(Long)obj).sum();
            long usedCount=pictureObjList.size();
            //封装返回结果
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(usedSize);
            //公共图库(或者全部空间)无容量限制,也没有空间使用比例
            spaceUsageAnalyzeResponse.setMaxSize(null);
            spaceUsageAnalyzeResponse.setSizeUsageRatio(null);
            spaceUsageAnalyzeResponse.setUsedCount(usedCount);
            //公共图库(或者全部空间)无数量限制,也没有数量使用比例
            spaceUsageAnalyzeResponse.setMaxCount(null);
            spaceUsageAnalyzeResponse.setCountUsageRatio(null);
            return spaceUsageAnalyzeResponse;
        } else {  //特定空间,仅从Space表来查询
            Long spaceId = spaceUsageAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
            Space space = spaceApplicationService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            //校验空间分析权限,仅本人/管理员可以访问特定空间
            checkSpaceAnalyzeAuth(spaceUsageAnalyzeRequest, loginUser);
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(space.getTotalSize());
            spaceUsageAnalyzeResponse.setMaxSize(space.getMaxSize());
            Double sizeUsageRatio = NumberUtil.round(space.getTotalSize()*100.0/space.getMaxSize(),2).doubleValue();
            spaceUsageAnalyzeResponse.setUsedCount(space.getTotalCount());
            spaceUsageAnalyzeResponse.setMaxCount(space.getMaxCount());
            Double countUsageRatio = NumberUtil.round(space.getTotalCount()*100.0/space.getMaxCount(),2).doubleValue();
            spaceUsageAnalyzeResponse.setCountUsageRatio(countUsageRatio);
            return   spaceUsageAnalyzeResponse;
        }



    }

    /**
     * 获取空间分类分析
     * @param spaceCategoryAnalyzeRequest 空间分类分析请求参数
     * @param loginUser 登录用户实体类
     * @return SpaceCategoryAnalyzeResponse 分析结果空间分类分析响应参数
     */
    @Override
    public List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceCategoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        //校验参数
        checkSpaceAnalyzeAuth(spaceCategoryAnalyzeRequest, loginUser);
        //构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceCategoryAnalyzeRequest, queryWrapper);
        //使用MayBatis构建查询条件
        queryWrapper.select("category", "count(*) as count", "sum(picSize) as totalSize");
        queryWrapper.groupBy("category");
        //执行查询
        return pictureApplicationService.getBaseMapper().selectMaps(queryWrapper).stream().map(result->{
            String category = (String) result.get("category");
            //不能强制转换为Long,否则会丢失精度
            Long count = Convert.toLong(result.get("count"));
            Long totalSize = Convert.toLong(result.get("totalSize"));
            return new SpaceCategoryAnalyzeResponse(category,count,totalSize);
        }).toList();

    }

    /**
     * 获取空间标签分析
     * @param spaceTagAnalyzeRequest 空间标签分析请求参数
     * @param loginUser 登录用户实体类
     * @return SpaceTagAnalyzeResponse 分析结果空间标签分析响应参数
     */
    @Override
    public List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceTagAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        //检查权限
        checkSpaceAnalyzeAuth(spaceTagAnalyzeRequest, loginUser);
        //构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceTagAnalyzeRequest, queryWrapper);
        //查询所有符合条件的标签
        queryWrapper.select("tags");
        List<String> tagsList = pictureApplicationService.getBaseMapper().selectObjs(queryWrapper)
                .stream()
                .filter(ObjectUtil::isNotNull)
                .map(Object::toString)
                .toList();
        //解析标签并统计
        //[“艺术"，"校园"，"高清"，“热门"]
        Map<String, Long> tagCountMap = tagsList.stream()
                .flatMap(tagJson -> JSONUtil.toList(tagJson, String.class).stream())//将标签字符串扁平化处理为标签列表,用于接下来Stream操作,：["[\"艺术\", \"校园\"]", "[\"高清\", \"热门\"]", "[\"艺术\", \"高清\"]"]->["艺术", "校园", "高清", "热门", "艺术", "高清"];
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));//{"艺术"=2, "校园"=1, "高清"=2, "热门"=1}

        // 转换为响应对象，按使用次数降序排序,并转换为List
        return tagCountMap.entrySet()
                .stream()
                .sorted((e1,e2)->Long.compare(e2.getValue(),e1.getValue()))//降序排序
                .map(entry->new SpaceTagAnalyzeResponse(entry.getKey(),entry.getValue()))//tag 标签名 :count 使用次数
                .toList();
    }

    /**
     * 获取空间大小分析,统计每个分段的图片数量,0-100KB, 100KB-500KB, 500KB-1MB, >1MB
     * @param spaceSizeAnalyzeRequest 空间大小分析请求参数
     * @param loginUser 登录用户实体类
     * @return SpaceSizeAnalyzeResponse 分析结果空间大小分析响应参数
     */
    @Override
    public List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser) {
        //校验参数
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        //1.检查权限
        checkSpaceAnalyzeAuth(spaceSizeAnalyzeRequest, loginUser);
        //2.构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceSizeAnalyzeRequest, queryWrapper);
        queryWrapper.select("picSize");
        //3.查询并转换结果
        List<Long> picSizes = pictureApplicationService.getBaseMapper().selectObjs(queryWrapper)
                .stream()
                .filter(ObjectUtil::isNotNull)
                .map(size->(Long)size)
                .toList();
        // 定义分段范围，注意使用有序 Map
        Map<String, Long> sizeRanges = new LinkedHashMap<>();
        //统计每个分段的图片数量,0-100KB, 100KB-500KB, 500KB-1MB, >1MB
        sizeRanges.put("<100KB", picSizes.stream().filter(size -> size < 100 * 1024).count());
        sizeRanges.put("100KB-500KB", picSizes.stream().filter(size -> size >= 100 * 1024 && size < 500 * 1024).count());
        sizeRanges.put("500KB-1MB", picSizes.stream().filter(size -> size >= 500 * 1024 && size < 1 * 1024 * 1024).count());
        sizeRanges.put(">1MB", picSizes.stream().filter(size -> size >= 1 * 1024 * 1024).count());
        //转换为响应对象,并转换为List
        return sizeRanges.entrySet()
                .stream()
                .map(entry->new SpaceSizeAnalyzeResponse(entry.getKey(),entry.getValue()))
                .toList();


    }

    /**
     * 基于图片的创建时间维度统计用户的上传行为，并按照时间升序排序：
     * @param spaceUserAnalyzeRequest 空间用户分析请求参数
     * @param loginUser 登录用户实体类
     * @return SpaceUserAnalyzeResponse 分析结果空间用户分析响应参数
     */
    @Override
    public List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser) {
        //校验参数
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        //检查权限
        checkSpaceAnalyzeAuth(spaceUserAnalyzeRequest, loginUser);
        //构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceUserAnalyzeRequest, queryWrapper);
        //补充用户id查询
        Long userId = spaceUserAnalyzeRequest.getUserId();
        queryWrapper.eq(ObjectUtil.isNotNull(userId),"userID", userId);
        //补充分析维度:每周,每日,每月
        String timeDimension = spaceUserAnalyzeRequest.getTimeDimension();
        switch (timeDimension) {
            case "day":
                queryWrapper.select("DATE_FORMAT(createTime,'%Y-%m-%d') as period","count(*) as count");
                break;
            case "week":
                queryWrapper.select("YEARWEEK(createTime) as period","count(*) as count");
                break;
            case "month":
                queryWrapper.select("DATE_FORMAT(createTime,'%Y-%m') as period","count(*) as count");
                break;
            default:
                ThrowUtils.throwIf(true, ErrorCode.PARAMS_ERROR, "时间维度错误");
        }
        //分组排序
        queryWrapper.groupBy("period").orderByAsc("period");
        //查询并封装返回结果,并转换为List
        return pictureApplicationService.getBaseMapper().selectMaps(queryWrapper).stream().map(result->{
            String period = Convert.toStr( result.get("period"));
            Long count = Convert.toLong(result.get("count"));
            return new SpaceUserAnalyzeResponse(period,count);
        }).toList();
    }

    /**
     * 获取空间排名分析(仅管理员)
     * @param spaceRankAnalyzeRequest 空间排名分析请求参数
     * @param loginUser 登录用户实体类
     * @return 空间排名分析响应参数
     */
    @Override
    public List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        //检查权限,仅管理员可以访问
        ThrowUtils.throwIf(!loginUser.isAdmin(), ErrorCode.NO_AUTH_ERROR);
        //构造查询条件
        QueryWrapper<Space>queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id","spaceName","userId","totalSize")
                .orderByDesc("totalSize")
                .last("limit "+spaceRankAnalyzeRequest.getTopN());//取前N名

        //查询并封装返回结果,并转换为List
        return spaceApplicationService.list(queryWrapper);



    }

    /**
     * 检查空间分析权限
     *
     * @param spaceAnalyzeRequest 空间分析请求参数
     * @param loginUser           登录用户实体类
     */

    private void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser) {

        boolean queryPublic = spaceAnalyzeRequest.isQueryPublic();
        boolean queryAll = spaceAnalyzeRequest.isQueryAll();
        // 全空间分析或者公共图库权限校验：仅管理员可访问
        if (queryAll || queryPublic) {
            ThrowUtils.throwIf(!loginUser.isAdmin(), ErrorCode.NO_AUTH_ERROR);
        } else {
            //分析特定空间,仅本人或者管理员可以访问
            Long spaceId = spaceAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
            Space space = spaceApplicationService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            //校验空间分析权限,仅本人或者管理员可以分析
            spaceApplicationService.checkSpaceAuth(loginUser, space);
        }
    }

    /**
     * 根据空间分析请求参数填充查询条件对象
     *
     * @param spaceAnalyzeRequest 空间分析请求参数
     * @param queryWrapper        查询条件对象
     */
    private void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest spaceAnalyzeRequest, QueryWrapper<Picture> queryWrapper) {

        boolean queryPublic = spaceAnalyzeRequest.isQueryPublic();
        boolean queryAll = spaceAnalyzeRequest.isQueryAll();
        //全空间分析
        if (queryAll) {
            return;
        }
        //公共图库
        if (queryPublic) {
            queryWrapper.isNull("spaceId");
            return;
        }
        //分析特定空间
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        if (spaceId != null) {
            queryWrapper.eq("spaceId", spaceId);
            return;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "未指定查询范围");


    }


}
