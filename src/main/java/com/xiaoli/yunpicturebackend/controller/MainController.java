package com.xiaoli.yunpicturebackend.controller;

import com.xiaoli.yunpicturebackend.common.BaseResponse;
import com.xiaoli.yunpicturebackend.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class MainController {
    /**轻量接口检测服务是否正常运行
     * 健康检查
     * @return ok
     */
    @GetMapping("/health")
    public BaseResponse<String> health() {
        return ResultUtils.success("ok");
    }
}
