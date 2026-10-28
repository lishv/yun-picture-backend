package com.xiaoli.yunpicture.infrastructure.messaging.picture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.amqp.Message;
import com.rabbitmq.client.amqp.Publisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 使用 RabbitMQ 4.4 AMQP 1.0 原生能力发布延迟图片清理消息。
 */
@Component
@RequiredArgsConstructor
public class PictureDelayPublisher {

    private static final long RETRY_DELAY_MILLIS = 1_800_000L;

    private final Publisher pictureDelayAmqp10Publisher;

    private final ObjectMapper objectMapper;

    public void publish(PictureDeleteMessage deleteMessage) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(deleteMessage);// 序列化为 JSON 字符串
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化图片延迟清理消息失败", exception);
        }

        Message message = pictureDelayAmqp10Publisher.message(body)
                .durable(true)
                .contentType("application/json")
                .annotation("x-opt-delivery-time", System.currentTimeMillis() + RETRY_DELAY_MILLIS);

        CompletableFuture<Publisher.Context> confirmation = new CompletableFuture<>();
        pictureDelayAmqp10Publisher.publish(message, confirmation::complete);

        try {
            Publisher.Context context = confirmation.get(5, TimeUnit.SECONDS);
            if (context.status() != Publisher.Status.ACCEPTED) {
                throw new IllegalStateException(
                        "RabbitMQ 未接受图片延迟清理消息，status: " + context.status(),
                        context.failureCause()
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 RabbitMQ 延迟消息确认时线程被中断", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("等待 RabbitMQ 延迟消息确认失败", exception);
        }
    }
}
