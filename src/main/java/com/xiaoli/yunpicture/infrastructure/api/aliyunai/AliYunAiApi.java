package com.xiaoli.yunpicture.infrastructure.api.aliyunai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.xiaoli.yunpicture.infrastructure.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.xiaoli.yunpicture.infrastructure.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.xiaoli.yunpicture.infrastructure.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.xiaoli.yunpicture.infrastructure.exception.BusinessException;
import com.xiaoli.yunpicture.infrastructure.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Slf4j
@Component
public class AliYunAiApi {
    //读取文件配置
    @Value("${aliYunAi.apiKey}")//不能为静态变量,否则会报错,因为静态变量在类加载时就赋值了,而这里是在运行时赋值的
    private String apiKey;
    @Value("${aliYunAi.apiHost}")//不能为静态变量,否则会报错,因为静态变量在类加载时就赋值了,而这里是在运行时赋值的
    private String apiHost;
    @Value("${aliYunAi.model}")
    private String model;

    private String CREATE_OUT_PAINTING_TASK_URL;
    private String GET_OUT_PAINTING_TASK_URL;
    @PostConstruct//在类加载完成后,调用init方法,初始化CREATE_OUT_PAINTING_TASK_URL和GET_OUT_PAINTING_TASK_URL
    public void init() {
        CREATE_OUT_PAINTING_TASK_URL = "https://" + apiHost + "/api/v1/services/aigc/image-generation/generation";
        GET_OUT_PAINTING_TASK_URL = "https://" + apiHost + "/api/v1/tasks/%s";
    }

    /**
     * 创建任务
     *
     * @param createOutPaintingTaskRequest 创建任务请求参数
     * @return 创建任务响应结果
     */
    public CreateOutPaintingTaskResponse createOutPaintingTask(CreateOutPaintingTaskRequest createOutPaintingTaskRequest) {
        if (createOutPaintingTaskRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        createOutPaintingTaskRequest.setModel(model);

        HttpRequest httpRequest = HttpRequest.post(CREATE_OUT_PAINTING_TASK_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("X-DashScope-Async", "enable")
                .header("Content-Type", "application/json")
                .body(JSONUtil.toJsonStr(createOutPaintingTaskRequest));

        try (HttpResponse httpResponse = httpRequest.execute()) {
            if (!httpResponse.isOk()) {
                log.error("请求异常:{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI绘图任务创建失败");
            }
            CreateOutPaintingTaskResponse createOutPaintingTaskResponse = JSONUtil.toBean(httpResponse.body(), CreateOutPaintingTaskResponse.class);
            if (createOutPaintingTaskResponse.getCode() != null) {
                String errorMessage = createOutPaintingTaskResponse.getMessage();
                log.error("请求异常:{}", errorMessage);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI绘图任务创建失败：" + errorMessage);
            }
            return createOutPaintingTaskResponse;
        }


    }

    /**
     * 查询创建的任务
     *
     * @param taskId
     * @return
     */
    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该任务ID不能为空");
        }
        String url = String.format(GET_OUT_PAINTING_TASK_URL, taskId);
        try (HttpResponse httpResponse = HttpRequest.get(url)
                .header("Authorization", "Bearer " + apiKey)
                .execute()) {
            if (!httpResponse.isOk()) {
                log.error("请求异常:{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取任务结果失败");
            }
            GetOutPaintingTaskResponse response = JSONUtil.toBean(httpResponse.body(), GetOutPaintingTaskResponse.class);
            if (response.getCode() != null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取AI绘图任务失败：" + response.getMessage());
            }
            if (response.getOutput() != null) {
                response.getOutput().setOutputImageUrl(response.getOutput().resolveOutputImageUrl());
            }
            return response;
        }

    }
}


