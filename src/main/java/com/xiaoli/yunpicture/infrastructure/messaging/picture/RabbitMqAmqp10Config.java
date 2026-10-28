package com.xiaoli.yunpicture.infrastructure.messaging.picture;

import com.rabbitmq.client.amqp.Connection;
import com.rabbitmq.client.amqp.Environment;
import com.rabbitmq.client.amqp.Publisher;
import com.rabbitmq.client.amqp.impl.AmqpEnvironmentBuilder;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * RabbitMQ AMQP 1.0 连接配置，仅用于 RabbitMQ 4.4 原生延迟消息发布。
 */
@Configuration
public class RabbitMqAmqp10Config {

    @Bean(destroyMethod = "close")
    public Environment amqp10Environment() {
        return new AmqpEnvironmentBuilder().build();
    }

    @Bean(destroyMethod = "close")
    public Connection amqp10Connection(
            Environment amqp10Environment,
            RabbitProperties rabbitProperties) {
        return amqp10Environment.connectionBuilder()
                .host(rabbitProperties.getHost())
                .port(rabbitProperties.getPort())
                .username(rabbitProperties.getUsername())
                .password(rabbitProperties.getPassword())
                .virtualHost(rabbitProperties.getVirtualHost())
                .name("yun-picture-delay-publisher")
                .build();
    }

    @Bean(destroyMethod = "close")
    public Publisher pictureDelayAmqp10Publisher(Connection amqp10Connection) {
        return amqp10Connection.publisherBuilder()
                .queue(MQPictureConstants.DELAY_Picture_QUEUE_NAME)
                .publishTimeout(Duration.ofSeconds(5))
                .build();
    }
}
