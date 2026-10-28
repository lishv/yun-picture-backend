package com.xiaoli.yunpicture.infrastructure.messaging.picture;

public interface MQPictureConstants {
    //正常队列
    String NORMAL_Picture_EXCHANGE_NAME = "normal.picture.direct";
    String NORMAL_Picture_QUEUE_NAME = "normal.picture.queue";
    String NORMAL_Picture_KEY = "normal.picture.query";
    // 延迟交换机和队列
    String DELAY_Picture_EXCHANGE_NAME = "delay.picture.direct";
    String DELAY_Picture_QUEUE_NAME = "delay.picture.queue";
    String DELAY_Picture_KEY = "delay.picture.query";
    // 死信
    String DLX_Picture_EXCHANGE_NAME = "dlx.picture.direct";
    String DLX_Picture_QUEUE_NAME = "dlx.picture.queue";
    String DLX_Picture_KEY = "dlx.picture.query";
}
