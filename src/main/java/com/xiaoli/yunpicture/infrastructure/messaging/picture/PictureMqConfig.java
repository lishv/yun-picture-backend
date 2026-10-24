package com.xiaoli.yunpicture.infrastructure.messaging.picture;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecovererWithConfirms;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.xiaoli.yunpicture.infrastructure.messaging.picture.MQPictureConstants;


@Configuration
@Slf4j
public class PictureMqConfig  {

    @Bean
    public Declarables pictureMqTopology() {

        DirectExchange normalExchange = new DirectExchange(MQPictureConstants.NORMAL_Picture_EXCHANGE_NAME);

        DirectExchange delayExchange = new DirectExchange(MQPictureConstants.DELAY_Picture_EXCHANGE_NAME);

        DirectExchange dlxExchange = new DirectExchange(MQPictureConstants.DLX_Picture_EXCHANGE_NAME);


        //1.正常队列
        Queue normalQueue = QueueBuilder
                .durable(MQPictureConstants.NORMAL_Picture_QUEUE_NAME)
                .build();

        //2.延迟队列,消息过期时间为 30 分钟,死信交换机为 NORMAL_Picture_EXCHANGE_NAME,死信路由键为 NORMAL_Picture_KEY
        // RabbitMQ 4.4 原生延迟重试队列
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
                normalExchange, delayExchange, dlxExchange,//交换机
                normalQueue, delayQueue, dlxQueue,//队列
                //绑定关系
                //1.正常队列绑定到正常交换机，路由键为 NORMAL_Picture_KEY
                BindingBuilder.bind(normalQueue)
                        .to(normalExchange)
                        .with(MQPictureConstants.NORMAL_Picture_KEY),
                //2.延迟队列绑定到延迟交换机，路由键为 DELAY_Picture_KEY
                BindingBuilder.bind(delayQueue)
                        .to(delayExchange)
                        .with(MQPictureConstants.DELAY_Picture_KEY),
                //3.死信队列绑定到死信交换机，路由键为 DLX_Picture_KEY
                BindingBuilder.bind(dlxQueue)
                        .to(dlxExchange)
                        .with(MQPictureConstants.DLX_Picture_KEY)
        );
    }

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public MessageRecoverer pictureMessageRecoverer(RabbitTemplate rabbitTemplate) {

        // 这个对象在创建 Bean 时初始化一次，后续复用
        //是 Spring 提供的工具：把失败消息重新发布到指定的交换机+路由键，并且等待 Broker 确认（publisher confirm）,也就是延迟重试队列。
        RepublishMessageRecovererWithConfirms delayRecoverer =
                new RepublishMessageRecovererWithConfirms(
                        rabbitTemplate,
                        MQPictureConstants.DELAY_Picture_EXCHANGE_NAME,
                        MQPictureConstants.DELAY_Picture_KEY,
                        CachingConnectionFactory.ConfirmType.CORRELATED //要求 Broker 返回确认，能把 confirm 和原消息对应起来。
                );

        delayRecoverer.setConfirmTimeout(5000);//设置确认超时时间为 5 秒

        return (message, cause) -> {
            //先判断消息来自哪个队列,这个消息是正常队列还是延迟队列还是死信队列
            String sourceQueue = message.getMessageProperties().getConsumerQueue();
            // 重试队列仍消费失败：不重入，由 DLX 转到最终死信队列

            // 分支一：来自延迟重试队列 → 直接进死信
            if (MQPictureConstants.DELAY_Picture_QUEUE_NAME.equals(sourceQueue)) {
                log.error("延迟重试仍失败，转入最终死信队列", cause);
                throw new AmqpRejectAndDontRequeueException("延迟删除图片仍然失败", cause);
            }

            // 分支二：来源不认识 → 立即重新入队
            if (!MQPictureConstants.NORMAL_Picture_QUEUE_NAME.equals(sourceQueue)) {
                throw new ImmediateRequeueAmqpException("无法识别图片清理消息的来源队列：" + sourceQueue, cause);
            }

            //分支三：来自正常队列 → 转交延迟重试队列
            //克隆一份消息（不改动原消息）。
            Message copy = MessageBuilder
                    .fromClonedMessage(message)
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)//持久化消息
//            设置 x-opt-delivery-time 头：这是 RabbitMQ 原生延迟队列的投递时间戳，now + 1800000ms（30 分钟）。
                    .setHeader("x-opt-delivery-time", System.currentTimeMillis() + 1_800_000L)
                    .build();

            try {
                //把克隆消息发到延迟交换机，同步等待 Broker confirm。
                delayRecoverer.recover(copy, cause);
                // 转交确认成功，正常返回，Spring AUTO ACK 原消息
                log.warn("消息已确认转入重试队列，30分钟后再次消费");
            } catch (Exception exception) {
                //转交失败，保留原消息， 原消息重新入
                log.error("转交重试队列失败，保留原消息", exception);
                throw new ImmediateRequeueAmqpException("图片清理消息转交失败", exception);
            }
        };
    }
}