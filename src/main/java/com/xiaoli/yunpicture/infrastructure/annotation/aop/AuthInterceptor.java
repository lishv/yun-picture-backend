package com.xiaoli.yunpicture.infrastructure.annotation.aop;

import com.xiaoli.yunpicture.infrastructure.annotation.AuthCheck;
import com.xiaoli.yunpicture.infrastructure.exception.BusinessException;
import com.xiaoli.yunpicture.infrastructure.exception.ErrorCode;
import com.xiaoli.yunpicture.domain.user.entity.User;
import com.xiaoli.yunpicture.domain.user.valueobject.UserRoleEnum;
import com.xiaoli.yunpicture.application.service.UserApplicationService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
public class AuthInterceptor {
    @Resource
    private UserApplicationService userApplicationService;

    /**
     * 权限校验拦截器
     * @param joinPoint 连接点
     * @param authCheck 注解
     * @return 执行结果
     */
    @Around("@annotation(authCheck)")
    public Object doIntercept(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String role = authCheck.mustRole();

        //获取当前登录用户
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        HttpServletRequest request = requestAttributes.getRequest();
        User loginUser = userApplicationService.getLoginUser(request);
        //要求当前请求角色
        UserRoleEnum roleEnum = UserRoleEnum.getEnumByValue(role);
        if (roleEnum == null) {
            //不需要权限,直接放行
            return joinPoint.proceed();
        }
        //需要权限,校验用户角色
        //实际获取用户角色
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        if (userRoleEnum == null) {
            //用户角色不存在,直接返回403
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        //要求必须是管理员角色,但用户角色不是管理员,拒绝
        if (UserRoleEnum.Admin.equals(roleEnum) && !UserRoleEnum.Admin.equals(userRoleEnum)) {
            //用户角色不是管理员,拒绝
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        //通过校验,放行
        return joinPoint.proceed();
    }
}
