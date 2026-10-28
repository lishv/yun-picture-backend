package com.xiaoli.yunpicture.infrastructure.messaging.picture;

import com.rabbitmq.client.amqp.Environment;
import com.rabbitmq.client.amqp.impl.AmqpEnvironmentBuilder;
import io.lettuce.core.RedisClient;
import org.junit.jupiter.api.Test;

class MessagingClientCompatibilityTest {

    @Test
    void shouldInitializeAmqp10AndRedisClientsWithUnifiedNettyVersion() {
        Environment environment = new AmqpEnvironmentBuilder().build();
        RedisClient redisClient = RedisClient.create("redis://localhost:6379");

        redisClient.shutdown();
        environment.close();
    }
}
