package com.xiaoli.yunpicture.infrastructure.api.aliyunai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetOutPaintingTaskResponse {

    /**
     * 请求唯一标识
     */
    private String requestId;

    /**
     * 请求失败时返回的错误码
     */
    private String code;

    /**
     * 请求失败时返回的错误信息
     */
    private String message;

    /**
     * 输出信息
     */
    private Output output;

    /**
     * 表示任务的输出信息
     */
    @Data
    public static class Output {

        /**
         * 任务 ID
         */
        private String taskId;

        /**
         * 任务状态
         * <ul>
         *     <li>PENDING: 排队中</li>
         *     <li>RUNNING: 处理中</li>
         *     <li>SUSPENDED: 挂起</li>
         *     <li>SUCCEEDED: 执行成功</li>
         *     <li>FAILED: 执行失败</li>
         *     <li>UNKNOWN: 任务不存在或状态未知</li>
         * </ul>
         */
        private String taskStatus;

        /**
         * 提交时间
         * 格式：YYYY-MM-DD HH:mm:ss.SSS
         */
        private String submitTime;

        /**
         * 调度时间
         * 格式：YYYY-MM-DD HH:mm:ss.SSS
         */
        private String scheduledTime;

        /**
         * 结束时间
         * 格式：YYYY-MM-DD HH:mm:ss.SSS
         */
        private String endTime;

        /**
         * 输出图像的 URL
         */
        private String outputImageUrl;

        /**
         * Wan2.7 生成结果
         */
        private List<Choice> choices;

        /**
         * 接口错误码。
         * <p>接口成功请求不会返回该参数。</p>
         */
        private String code;

        /**
         * 接口错误信息。
         * <p>接口成功请求不会返回该参数。</p>
         */
        private String message;

        /**
         * 任务指标信息
         */
        private TaskMetrics taskMetrics;

        public String resolveOutputImageUrl() {
            if (choices == null) {
                return null;
            }
            for (Choice choice : choices) {
                if (choice.getMessage() == null || choice.getMessage().getContent() == null) {
                    continue;
                }
                for (Content content : choice.getMessage().getContent()) {
                    if ("image".equals(content.getType())) {
                        return content.getImage();
                    }
                }
            }
            return null;
        }
    }

    @Data
    public static class Choice {
        private String finishReason;
        private Message message;
    }

    @Data
    public static class Message {
        private String role;
        private List<Content> content;
    }

    @Data
    public static class Content {
        private String type;
        private String image;
        private String text;
    }

    /**
     * 表示任务的统计信息
     */
    @Data
    public static class TaskMetrics {

        /**
         * 总任务数
         */
        private Integer total;

        /**
         * 成功任务数
         */
        private Integer succeeded;

        /**
         * 失败任务数
         */
        private Integer failed;
    }//   private Usage usage;
//    /**
//     * 图像统计信息
//     */
//    @Data
//    public static class Usage {
//
//        /**
//         * 模型成功生成图片的数量
//         */
//
//        private Integer imageCount;
//    }
}
