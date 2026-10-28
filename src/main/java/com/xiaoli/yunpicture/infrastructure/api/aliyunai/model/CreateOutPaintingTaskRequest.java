package com.xiaoli.yunpicture.infrastructure.api.aliyunai.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class CreateOutPaintingTaskRequest implements Serializable {

    /**
     * 阿里云图像生成模型
     */
    private String model;

    /**
     * 输入图像信息
     */
    private Input input;

    /**
     * 图像处理参数
     */
    private Parameters parameters;

    @Data
    public static class Input {
        /**
         * 用户消息
         */
        private List<Message> messages;
    }

    @Data
    public static class Message implements Serializable {
        /**
         * 消息角色，固定为 user
         */
        private String role;

        /**
         * 当前图片和用户提示词
         */
        private List<Content> content;
    }

    @Data
    public static class Content implements Serializable {
        /**
         * 参考图片 URL
         */
        private String image;

        /**
         * 用户提示词
         */
        private String text;
    }

    @Data
    public static class Parameters implements Serializable {
        /**
         * 输出图片规格
         */
        private String size = "2K";

        /**
         * 生成图片数量
         */
        private Integer n = 1;

        /**
         * 是否添加水印
         */
        private Boolean watermark = false;
    }
}
