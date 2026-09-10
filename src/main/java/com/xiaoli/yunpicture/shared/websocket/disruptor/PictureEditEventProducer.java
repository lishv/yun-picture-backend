package com.xiaoli.yunpicture.shared.websocket.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.xiaoli.yunpicture.shared.websocket.model.PictureEditRequestMessage;
import com.xiaoli.yunpicture.domain.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;

/**
 * 用于生产消息,在下一个环形位置处
 */
@Component
@Slf4j
public class PictureEditEventProducer {

    @Resource
    Disruptor<PictureEditEvent> pictureEditEventDisruptor;

    /**
     * 发布事件到 Disruptor 缓冲区
     * @param pictureEditRequestMessage 图片编辑请求消息
     * @param session WebSocket 会话
     * @param user 用户对象
     * @param pictureId 图片 ID
     */
    public void publishEvent(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) {
        RingBuffer<PictureEditEvent> ringBuffer = pictureEditEventDisruptor.getRingBuffer();// 获取 ringBuffer缓冲区,用于发布事件
        // 获取可以生成的位置
        long next = ringBuffer.next();
        PictureEditEvent pictureEditEvent = ringBuffer.get(next);
        pictureEditEvent.setSession(session);
        pictureEditEvent.setPictureEditRequestMessage(pictureEditRequestMessage);
        pictureEditEvent.setUser(user);
        pictureEditEvent.setPictureId(pictureId);
        // 发布事件
        ringBuffer.publish(next);
    }

    /**
     * 优雅停机,等待所有事件处理完成在结束,类似idea里点击关闭项目与任务管理器关闭进程区别
     */
    @PreDestroy
    public void close() {

        pictureEditEventDisruptor.shutdown();

    }
}