package com.xiaoli.yunpicture.infrastructure.messaging.picture;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * RabbitMQ 消息发送工具。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RabbitMqHelper {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送持久化消息并等待 Broker Confirm。
     *
     * 只有同时满足下面两个条件才视为发送成功：
     * 1. Broker 返回 ACK；
     * 2. 消息没有因为无法路由而被 Return。
     *
     * 注意：该方法会等待确认，只能在后台线程中调用，
     * 不能直接放在 HTTP 请求线程中执行。
     */
    public void sendPersistentMessageWithConfirm(String exchange, String routingKey, Object messageBody) {
        //CorrelationData 是 Spring AMQP 用来**关联"发出的消息"和"Broker 返回的确认"**的对象。
        //给它一个唯一 ID（UUID），这样 Broker 回 ACK 时，我们能知道是哪条消息的确认。
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());


        log.debug( "准备发送持久化消息，exchange: {}，routingKey: {}，correlationId: {}", exchange, routingKey, correlationData.getId());
        //异步发出，但设置了消息属性
        /*
        convertAndSend 的 5 个参数：
            exchange：发到哪个交换机
            routingKey：路由键
            messageBody：消息体（会被 MessageConverter 序列化）
            MessagePostProcessor（Lambda）：消息发送前的处理器
            设置 PERSISTENT（持久化）：消息会写入磁盘，Broker 重启不丢
            correlationData：把关联数据传进去，后续用它等确认
         */
        rabbitTemplate.convertAndSend(exchange,routingKey, messageBody,
                message -> {
                    //设置消息持久化
                    message.getMessageProperties() .setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                },
                correlationData
        );
        /*

            生产者发消息时携带 correlationData（含唯一ID）
                    │
                    ▼
            Broker 收到消息后，回一个 ACK，ACK 里带着同样的 correlationId
                    │
                    ▼
            生产者通过 correlationData.getFuture() 拿到这个 ACK
         */
/**
 *
 *
 * 这里确认的是哪一层？
 *
 *
 *
 *
 * 生产者 ──发消息──> Broker(交换机→队列) ──投递──> 消费者
 *               ↑                              ↑
 *          这里确认的是这一步               不是这一步
 */

        try {

            //correlationData.getFuture() 拿到一个 CompletableFuture<Confirm>。
            // .get(5, TimeUnit.SECONDS)：阻塞当前线程，最多等 5 秒拿确认结果。
            //5 秒内没拿到 → 抛 TimeoutException。
            //Broker 返回确认后，Future 完成，拿到 Confirm 对象。
            CorrelationData.Confirm confirm = correlationData
                    .getFuture()
                    .get(5, TimeUnit.SECONDS);


            //confirm == null：没拿到确认对象。
            //!confirm.isAck()：Broker 返回了 NACK（否定确认），说明 Broker 没成功接收消息。
            //任一情况都抛异常，发送失败。
            if (confirm == null || !confirm.isAck()) {
                String reason = confirm == null ? "没有收到 Broker Confirm" : confirm.getReason();
                throw new AmqpException("RabbitMQ 消息发送未被确认：" + reason);
            }

            //即使 Broker ACK 了，也可能消息到了交换机但找不到匹配的队列。
            //这时 Broker 会把消息 Return 回来，correlationData.getReturned() 不为 null。
            //比如路由键写错了、队列没绑定，就会触发这个。只有 ACK 成功 且 没有被 Return，才算真正发送成功。
            if (correlationData.getReturned() != null) {
                throw new AmqpException("RabbitMQ 消息无法路由：" +correlationData.getReturned().getReplyText());
            }
            log.debug("RabbitMQ 消息发送成功，correlationId: {}",correlationData.getId());

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();// // 恢复中断标记（InterruptedException 会清除该标记），让上层能感知中断

            throw new AmqpException( "等待 RabbitMQ Confirm 时线程被中断", exception);
            //confirm == null：没拿到确认对象。!confirm.isAck()：Broker 返回了 NACK（否定确认）
            // ，说明 Broker 没成功都统一包装成 AmqpException 抛出，让调用方决定怎么处理。直接nack了
        } catch (ExecutionException | TimeoutException exception) {
            //都统一包装成 AmqpException 抛出，让调用方决定怎么处理。
            throw new AmqpException("等待 RabbitMQ Confirm 失败", exception);
        }
    }
}