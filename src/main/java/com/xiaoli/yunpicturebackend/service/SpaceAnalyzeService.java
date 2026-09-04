package com.xiaoli.yunpicturebackend.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoli.yunpicturebackend.model.dto.space.analyze.*;
import com.xiaoli.yunpicturebackend.model.entity.Space;
import com.xiaoli.yunpicturebackend.model.entity.User;
import com.xiaoli.yunpicturebackend.model.vo.analyze.*;

import java.util.List;

public interface SpaceAnalyzeService extends IService<Space> {
    /**
     * 获取空间使用分析
     * @param spaceUsageAnalyzeRequest 空间使用分析请求参数
     * @param loginUser 登录用户实体类
     * @return 空间使用分析响应参数
     */
     SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser) ;

    /**
     * 获取空间分类分析
     * @param spaceCategoryAnalyzeRequest 空间分类分析请求参数
     * @param loginUser 登录用户实体类
     * @return 空间分类分析响应参数
     */
    List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser);
    /**
     * 获取空间标签分析
     * @param spaceTagAnalyzeRequest 空间标签分析请求参数
     * @param loginUser 登录用户实体类
     * @return 空间标签分析响应参数
     */
    List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser);

    /**
     * 获取空间大小分析,统计每个分段的图片数量,0-100KB, 100KB-500KB, 500KB-1MB, >1MB
     * @param spaceSizeAnalyzeRequest 空间大小分析请求参数
     * @param loginUser 登录用户实体类
     * @return 空间大小分析响应参数
     */
    List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser);

    /**
     * 基于图片的创建时间维度统计用户的上传行为，并按照时间升序排序
     * @param spaceUserAnalyzeRequest 空间用户分析请求参数
     * @param loginUser 登录用户实体类
     * @return 空间用户分析响应参数
     */
    List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser);
    /**
     * 获取空间排名分析(仅管理员)
     * @param spaceRankAnalyzeRequest 空间排名分析请求参数
     * @param loginUser 登录用户实体类
     * @return 空间排名分析响应参数
     */
    List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser);

}

