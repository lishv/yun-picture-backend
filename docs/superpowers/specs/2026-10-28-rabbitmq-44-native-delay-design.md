# RabbitMQ 4.4 原生延迟消息设计

## 目标

将图片清理首轮失败后的 30 分钟延迟重试改为 RabbitMQ 4.4 quorum queue 原生延迟消息，避免使用 AMQP 0-9-1 普通 Header 模拟 AMQP 1.0 的 `x-opt-delivery-time`。

同时将图片清理告警收件人配置从公共 `application.yml` 移到 `application-local.yml` 和 `application-prod.yml`。

## 协议边界

- Spring AMQP / AMQP 0-9-1 保留负责：队列和交换机声明、普通清理消息发布、正常队列消费、延迟队列消费、最终死信消费。
- RabbitMQ 官方 AMQP 1.0 Java Client 仅负责：把首轮失败消息以未来投递时间发布到现有 quorum 延迟队列。
- 不迁移现有消费者，不升级 Spring Boot，不引入延迟交换机插件，不改为 TTL 加 DLX。

## 依赖

增加 RabbitMQ 官方 AMQP 1.0 Java Client：

```xml
<dependency>
    <groupId>com.rabbitmq.client</groupId>
    <artifactId>amqp-client</artifactId>
    <version>1.6.0</version>
</dependency>
```

该依赖与 Spring AMQP 使用的 `com.rabbitmq:amqp-client` 坐标不同，可以并存。

## 连接与发布器

新增 AMQP 1.0 配置，复用现有 `spring.rabbitmq.host`、`port`、`username`、`password` 和 `virtual-host`：

- 应用启动时创建一个 AMQP 1.0 `Environment`。
- 创建一个 AMQP 1.0 `Connection`。
- 创建一个线程安全的 `Publisher`，目标为现有延迟交换机和延迟路由键。
- 应用关闭时依次关闭 Publisher、Connection 和 Environment。

不增加第二套 RabbitMQ 配置项。

## 延迟发布

新增图片清理延迟发布器：

1. 使用现有 `ObjectMapper` 将 `PictureDeleteMessage` 序列化为 JSON 字节。
2. 创建 AMQP 1.0 消息并设置 `content-type: application/json`。
3. 设置 message annotation：

   ```text
   x-opt-delivery-time = 当前 Unix 毫秒时间 + 1,800,000
   ```

4. 发布到现有 `delay.picture.direct`，路由键为 `delay.picture.query`。
5. 等待 Broker 发布结果，只有 `ACCEPTED` 才视为成功。
6. 发布被拒绝、释放、异常或确认超时时，向上抛出异常。

现有延迟队列保持 quorum queue。RabbitMQ 路由后立即确认已安全保存，但消费者只能在未来投递时间到达后收到消息。

## Recoverer 调整

正常队列本地重试耗尽后：

1. 调用 AMQP 1.0 延迟发布器。
2. 发布确认失败时抛出 `ImmediateRequeueAmqpException`，正常队列原消息重新入队。
3. 发布确认成功后，将数据库任务状态更新为 `WAITING_RETRY`。
4. Recoverer 正常返回，由 Spring AUTO ACK 正常队列原消息。

延迟队列第二轮失败的处理保持不变：先将任务标记为 `FAILED`，再拒绝消息，使其进入最终死信队列。

## 消息兼容

AMQP 1.0 延迟发布器发送 JSON 字节并声明 `application/json`。延迟队列仍由现有 Spring AMQP Jackson 消息转换器消费，消息体继续映射为 `PictureDeleteMessage`，不改变业务消息结构。

## 邮箱配置移动

从 `application.yml` 删除：

```yaml
picture:
  cleanup:
    alert-email: ...
```

分别在 `application-local.yml` 和 `application-prod.yml` 增加：

```yaml
picture:
  cleanup:
    alert-email: ${PICTURE_CLEANUP_ALERT_EMAIL:2570410870@qq.com}
```

## 不做的修改

- 不修改正常消息发布器和待发送任务扫描器。
- 不改变数据库任务状态定义。
- 不新增队列、交换机或路由键。
- 不修改30分钟后的第二轮清理逻辑。
- 不改变最终死信邮件的30分钟 Redis 冷却。
- 不修改前端。

## 验证

- AMQP 1.0 发布消息包含绝对时间形式的 `x-opt-delivery-time` annotation。
- Broker 返回 `ACCEPTED` 时延迟发布成功。
- 非 `ACCEPTED`、异常和超时会导致正常队列原消息重新入队。
- 延迟消息序列化结果可以被现有 Jackson 消息转换器映射为 `PictureDeleteMessage`。
- local 和 prod 均能解析管理员告警邮箱，公共配置不再包含该值。
- Java 17 下完成针对性单元测试和打包验证。
