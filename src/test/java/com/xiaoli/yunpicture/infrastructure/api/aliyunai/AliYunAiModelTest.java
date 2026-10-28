package com.xiaoli.yunpicture.infrastructure.api.aliyunai;

import cn.hutool.json.JSONUtil;
import com.xiaoli.yunpicture.infrastructure.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.xiaoli.yunpicture.infrastructure.api.aliyunai.model.GetOutPaintingTaskResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AliYunAiModelTest {

    @Test
    void shouldSerializeWan27ImageEditRequest() {
        CreateOutPaintingTaskRequest request = new CreateOutPaintingTaskRequest();
        request.setModel("wan2.7-image");

        CreateOutPaintingTaskRequest.Content image = new CreateOutPaintingTaskRequest.Content();
        image.setImage("https://example.com/source.webp");
        CreateOutPaintingTaskRequest.Content text = new CreateOutPaintingTaskRequest.Content();
        text.setText("将背景改为夜晚的城市");

        CreateOutPaintingTaskRequest.Message message = new CreateOutPaintingTaskRequest.Message();
        message.setRole("user");
        message.setContent(List.of(image, text));

        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setMessages(List.of(message));
        request.setInput(input);
        request.setParameters(new CreateOutPaintingTaskRequest.Parameters());

        String json = JSONUtil.toJsonStr(request);

        assertEquals("wan2.7-image", JSONUtil.parseObj(json).getStr("model"));
        assertEquals("https://example.com/source.webp",
                JSONUtil.parseObj(json).getJSONObject("input")
                        .getJSONArray("messages").getJSONObject(0)
                        .getJSONArray("content").getJSONObject(0).getStr("image"));
        assertEquals("将背景改为夜晚的城市",
                JSONUtil.parseObj(json).getJSONObject("input")
                        .getJSONArray("messages").getJSONObject(0)
                        .getJSONArray("content").getJSONObject(1).getStr("text"));
        assertFalse(JSONUtil.parseObj(json).getJSONObject("input")
                .getJSONArray("messages").getJSONObject(0)
                .getJSONArray("content").getJSONObject(0).containsKey("text"));
        assertFalse(JSONUtil.parseObj(json).getJSONObject("input")
                .getJSONArray("messages").getJSONObject(0)
                .getJSONArray("content").getJSONObject(1).containsKey("image"));
        assertEquals("2K", JSONUtil.parseObj(json).getJSONObject("parameters").getStr("size"));
        assertEquals(1, JSONUtil.parseObj(json).getJSONObject("parameters").getInt("n"));
        assertEquals(false, JSONUtil.parseObj(json).getJSONObject("parameters").getBool("watermark"));
    }

    @Test
    void shouldResolveFirstGeneratedImageFromWan27TaskResult() {
        String body = "{\"output\":{\"task_id\":\"task-1\",\"task_status\":\"SUCCEEDED\"," +
                "\"choices\":[{\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"done\"}," +
                "{\"type\":\"image\",\"image\":\"https://example.com/generated.png\"}]}}]}}";

        GetOutPaintingTaskResponse response = JSONUtil.toBean(body, GetOutPaintingTaskResponse.class);

        assertEquals("https://example.com/generated.png", response.getOutput().resolveOutputImageUrl());
    }
}
