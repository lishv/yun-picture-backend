package com.xiaoli.yunpicture.infrastructure.messaging.picture;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@Configuration
@Slf4j
public class PictureMqConfig  {

    @Bean
    public Declarables pictureMqTopology() {

        DirectExchange normalExchange = new DirectExchange(MQPictureConstants.NORMAL_Picture_EXCHANGE_NAME);

        DirectExchange dlxExchange = new DirectExchange(MQPictureConstants.DLX_Picture_EXCHANGE_NAME);


        //1.正常队列
        Queue normalQueue = QueueBuilder
                .durable(MQPictureConstants.NORMAL_Picture_QUEUE_NAME)
                .build();

        //2.RabbitMQ 4.4 quorum queue 原生延迟消息队列
        Queue delayQueue = QueueBuilder
                .durable(MQPictureConstants.DELAY_Picture_QUEUE_NAME)
                .quorum()//开启高可用
                // 再次消费失败时，进入最终死信队列
                .deadLetterExchange(MQPictureConstants.DLX_Picture_EXCHANGE_NAME)
                .deadLetterRoutingKey(MQPictureConstants.DLX_Picture_KEY)
                .withArgument("x-dead-letter-strategy", "at-least-once")
                .withArgument("x-overflow", "reject-publish")
                .build();

        Queue dlxQueue = QueueBuilder
                .durable(MQPictureConstants.DLX_Picture_QUEUE_NAME)
                .build();

        return new Declarables(//可以添加多个交换机、队列、绑定关系
                normalExchange, dlxExchange,//交换机
                normalQueue, delayQueue, dlxQueue,//队列
                //绑定关系
                //1.正常队列绑定到正常交换机，路由键为 NORMAL_Picture_KEY
                BindingBuilder.bind(normalQueue)
                        .to(normalExchange)
                        .with(MQPictureConstants.NORMAL_Picture_KEY),
                //2.死信队列绑定到死信交换机，路由键为 DLX_Picture_KEY
                BindingBuilder.bind(dlxQueue)
                        .to(dlxExchange)
                        .with(MQPictureConstants.DLX_Picture_KEY)
        );
    }

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     *  首轮消费失败后，把消息可靠地重新发布到延迟队列，
     *   并等待 Broker Confirm。
     * @param objectMapper
     * @param taskService
     * @param delayPublisher RabbitMQ 4.4 原生延迟消息发布器
     * @return
     */

    @Bean
    public MessageRecoverer pictureMessageRecoverer(
            ObjectMapper objectMapper,
            PictureCleanupTaskService taskService,
            PictureDelayPublisher delayPublisher) {
        return (message, cause) -> {
            String sourceQueue = message.getMessageProperties().getConsumerQueue();

            Long taskId = extractTaskId(objectMapper, message);

            /*
             * 第二轮：延迟队列重试仍然失败。
             */
            if (MQPictureConstants.DELAY_Picture_QUEUE_NAME
                    .equals(sourceQueue)) {
                /*
                 * 必须先把失败状态保存到数据库，
                 * 再拒绝消息进入最终死信队列。
                 */
                if (taskId != null) {
                    try {taskService.markFailed(taskId, cause);
                    } catch (Exception statusException) {
                        /*
                         * 数据库失败状态没有保存成功时不能直接死信，
                         * 否则超级管理员将无法在任务表中看到它。
                         */
                        log.error("记录图片清理最终失败状态失败，延迟消息重新入队，taskId: {}", taskId, statusException);

                        throw new ImmediateRequeueAmqpException("记录图片清理最终失败状态失败", statusException);
                    }
                }

                log.error("图片延迟清理仍然失败，转入最终死信队列，taskId: {}", taskId, cause);

                /*
                 * requeue=false，由延迟队列配置的 DLX
                 * 将消息转入最终死信队列。
                 */
                throw new AmqpRejectAndDontRequeueException("延迟删除图片仍然失败", cause);
            }

            /*
             * 防止其他未知队列错误使用当前 Recoverer。
             */
            if (!MQPictureConstants.NORMAL_Picture_QUEUE_NAME.equals(sourceQueue)) {
                throw new ImmediateRequeueAmqpException("无法识别图片清理消息来源队列：" + sourceQueue, cause);
            }

            /*
             * 这里是正常队列发送失败后发送到延迟队列的情况。
             * 转交失败时，正常队列原消息必须重新入队。
             */
            try {
                PictureDeleteMessage deleteMessage = new PictureDeleteMessage();
                deleteMessage.setTaskId(taskId);
                delayPublisher.publish(deleteMessage);
            } catch (Exception publishException) {
                log.error("转交延迟重试队列失败，正常队列原消息重新入队，taskId: {}", taskId, publishException);

                throw new ImmediateRequeueAmqpException("图片清理消息转交延迟队列失败", publishException);
            }

            /*
             * 到这里说明 Broker 已经确认延迟消息。
             *
             * 如果数据库状态更新偶然失败，不能 NACK 原消息，
             * 因为延迟消息已经成功存在于 RabbitMQ 中。
             * 延迟消费者也允许从 PROCESSING 状态继续处理。
             */
            if (taskId != null) {
                try {
                    taskService.markWaitingRetry(taskId, cause);
                } catch (Exception statusException) {
                    log.error("延迟消息已确认，但更新等待重试状态失败，taskId: {}", taskId, statusException);
                }
            }

            /*
             * Recoverer 正常返回：
             * Spring AUTO ACK 正常队列中的原消息。
             */
            log.warn("图片清理消息已确认转入延迟队列，将在30分钟后再次消费，taskId: {}", taskId);
        };
    }
    /**
     * 从 RabbitMQ JSON 消息中读取任务 id。
     *
     * 解析失败时返回 null，消息仍会按照当前队列规则
     * 进入延迟队列或最终死信队列，避免毒消息无限重入。
     */
    private Long extractTaskId(ObjectMapper objectMapper, Message message) {
        try {
            PictureDeleteMessage deleteMessage = objectMapper.readValue(message.getBody(), PictureDeleteMessage.class);

            return deleteMessage == null ? null : deleteMessage.getTaskId();
        } catch (Exception exception) {log.error("无法解析图片清理消息中的 taskId", exception);return null;}
    }

}
