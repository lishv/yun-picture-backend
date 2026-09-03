package com.xiaoli.yunpicturebackend.api.aliyunai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.xiaoli.yunpicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.xiaoli.yunpicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.xiaoli.yunpicturebackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.xiaoli.yunpicturebackend.exception.BusinessException;
import com.xiaoli.yunpicturebackend.exception.ErrorCode;
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
    private String CREATE_OUT_PAINTING_TASK_URL;
    private String GET_OUT_PAINTING_TASK_URL;
    @PostConstruct//在类加载完成后,调用init方法,初始化CREATE_OUT_PAINTING_TASK_URL和GET_OUT_PAINTING_TASK_URL
    public void init() {
        CREATE_OUT_PAINTING_TASK_URL = "https://" + apiHost + "/api/v1/services/aigc/image2image/out-painting";
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

        //# 以下为华北2（北京）地域的URL，调用时请将 {WorkspaceId} 替换为真实的业务空间ID，各地域的URL不同。
        //curl --location --request POST 'https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/image2image/out-painting' \
        //--header "Authorization: Bearer $DASHSCOPE_API_KEY" \
        //--header 'X-DashScope-Async: enable' \
        //--header 'Content-Type: application/json' \
        //--data '{
        //    "model": "image-out-painting",
        //    "input": {
        //        "image_url": "http://xxx/image.jpg"
        //    },
        //    "parameters":{
        //        "angle": 45,
        //        "x_scale":1.5,
        //        "y_scale":1.5
        //    }
        //}'
        HttpRequest httpRequest = HttpRequest.post(CREATE_OUT_PAINTING_TASK_URL)
                .header("Authorization", "Bearer " + apiKey)
                //必须开启异步处理
                .header("X-DashScope-Async", "enable")
                .header("Content-Type", "application/json")
                .body(JSONUtil.toJsonStr(createOutPaintingTaskRequest));//@Alias生效了,使用hutool的json工具类转换为json字符串，阿里云那边收到的是json字符串

        try (HttpResponse httpResponse = httpRequest.execute()) {
            if (!httpResponse.isOk()) {
                log.error("请求异常:{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI括图失败");
            }
            //{//正常响应
            //    "output": {
            //        "task_status": "PENDING",
            //        "task_id": "0385dc79-5ff8-4d82-bcb6-xxxxxx"
            //    },
            //    "request_id": "4909100c-7b5a-9f92-bfe5-xxxxxx"
            //}
            //{//异常响应
            //    "code": "InvalidApiKey",
            //    "message": "No API-key provided.",
            //    "request_id": "7438d53d-6eb8-4596-8835-xxxxxx"
            //}
            //返回的也是json字符串，需要转换为对象
            CreateOutPaintingTaskResponse createOutPaintingTaskResponse = JSONUtil.toBean(httpResponse.body(), CreateOutPaintingTaskResponse.class);
            if (createOutPaintingTaskResponse.getCode() != null) {
                String errorMessage = createOutPaintingTaskResponse.getMessage();
                log.error("请求异常:{}", errorMessage);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI括图失败" + errorMessage);
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
        //curl -X GET https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/api/v1/tasks/86ecf553-d340-4e21-xxxxxxxxx \
        //--header "Authorization: Bearer $DASHSCOPE_API_KEY"
        String url = String.format(GET_OUT_PAINTING_TASK_URL, taskId);
        try (HttpResponse httpResponse = HttpRequest.get(url)
                .header("Authorization", "Bearer " + apiKey)
                .execute()) {
            //{
            //    "request_id": "b67df059-ca6a-9d51-afcd-9b3c4456b1e2",
            //    "output": {
            //        "task_id": "d76ec1e8-ea27-4038-8913-235c88ef0f70",
            //        "task_status": "SUCCEEDED",
            //        "submit_time": "2024-05-16 13:50:01.247",
            //        "scheduled_time": "2024-05-16 13:50:01.354",
            //        "end_time": "2024-05-16 13:50:27.795",
            //        "output_image_url": "https://xxxx/xxxx"
            //    },
            //    "usage": {
            //        "image_count": 1
            //    }
            //}
            if (!httpResponse.isOk()) {
                log.error("请求异常:{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取任务结果失败");
            }
            return JSONUtil.toBean(httpResponse.body(), GetOutPaintingTaskResponse.class);
        }

    }
}


