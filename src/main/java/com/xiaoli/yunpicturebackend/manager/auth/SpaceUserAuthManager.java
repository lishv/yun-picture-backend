package com.xiaoli.yunpicturebackend.manager.auth;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.xiaoli.yunpicture.application.service.SpaceUserApplicationService;
import com.xiaoli.yunpicturebackend.manager.auth.model.SpaceUserAuthConfig;
import com.xiaoli.yunpicturebackend.manager.auth.model.SpaceUserRole;
import com.xiaoli.yunpicture.domain.space.entity.Space;
import com.xiaoli.yunpicture.domain.space.entity.SpaceUser;
import com.xiaoli.yunpicture.domain.user.entity.User;
import com.xiaoli.yunpicture.domain.space.valueobject.SpaceRoleEnum;
import com.xiaoli.yunpicture.domain.space.valueobject.SpaceTypeEnum;

import com.xiaoli.yunpicture.application.service.UserApplicationService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 空间成员权限管理
 */
@Component
public class SpaceUserAuthManager {
    @Resource
    private UserApplicationService userApplicationService;
    @Resource
    private SpaceUserApplicationService spaceUserApplicationService;

    public  static  final SpaceUserAuthConfig SPACE_USER_AUTH_CONFIG;
    static {
        // 读取空间用户权限配置文件
        String json = ResourceUtil.readUtf8Str("biz/spaceUserAuthConfig.json");
        SPACE_USER_AUTH_CONFIG =JSONUtil.toBean(json,SpaceUserAuthConfig.class);
    }

    /**
     * 根据角色获取权限列表
     * @param spaceUserRole 角色名称
     * @return 权限列表
     */
    public List<String> getPermissionsByRole(String spaceUserRole) {
        // 从空间用户权限配置中获取角色对应的权限列表
        if(StrUtil.isBlank(spaceUserRole)){
            return  new ArrayList<>();
        }
        SpaceUserRole role = SPACE_USER_AUTH_CONFIG.getRoles().stream()
                .filter(r -> r.getKey().equals(spaceUserRole))
                .findFirst()
                .orElse(null);
        if(role==null){
            return  new ArrayList<>();
        }

        return role.getPermissions();
    }

    /**
     * 获取登录用户在空间内的权限列表
     * @param space 空间信息
     * @param loginUser 登录用户信息
     * @return 权限列表
     */
    public List<String> getPermissionList(Space space, User loginUser) {
        if (loginUser == null) {
            return new ArrayList<>();
        }
        // 管理员权限
        List<String> ADMIN_PERMISSIONS = getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
        // 公共图库,仅管理员有所有权限
        if (space == null) {
            if (loginUser.isAdmin()) {
                return ADMIN_PERMISSIONS;
            }
            return new ArrayList<>();
        }
        //根据控件类型获取权限列表
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(space.getSpaceType());
        if (spaceTypeEnum == null) {
            return new ArrayList<>();
        }
        // 根据空间获取对应的权限
        switch (spaceTypeEnum) {
            case PRIVATE:
                // 私有空间，仅本人或管理员有所有权限
                if (space.getUserId().equals(loginUser.getId()) || loginUser.isAdmin()) {
                    return ADMIN_PERMISSIONS;
                } else {
                    return new ArrayList<>();
                }
            case TEAM:
                // 团队空间，查询 SpaceUser 并获取角色和权限,查询该用户在空间中的角色和权限
                SpaceUser spaceUser = spaceUserApplicationService.lambdaQuery()
                        .eq(SpaceUser::getSpaceId, space.getId())
                        .eq(SpaceUser::getUserId, loginUser.getId())
                        .one();
                if (spaceUser == null) {
                    return new ArrayList<>();
                } else {
                    return getPermissionsByRole(spaceUser.getSpaceRole());
                }
        }
        return new ArrayList<>();
    }

}
