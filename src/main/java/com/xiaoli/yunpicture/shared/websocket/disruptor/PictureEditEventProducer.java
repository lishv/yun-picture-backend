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
     * 发布事件到 Disruptor 缓冲区,业务处理全在 Disruptor 的工作线程里异步完成，I/O 线程不被阻塞
     * @param pictureEditRequestMessage 图片编辑请求消息
     * @param session WebSocket 会话
     * @param user 用户对象
     * @param pictureId 图片 ID
     */
    public void publishEvent(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) {
        RingBuffer<PictureEditEvent> ringBuffer = pictureEditEventDisruptor.getRingBuffer();// 获取 ringBuffer缓冲区,用于发布事件
        //  申请一个可用的事件序列位置。
        long next = ringBuffer.next();
        //取出这个位置给预先准备好的事件对象。
        PictureEditEvent pictureEditEvent = ringBuffer.get(next);
        //  把本次 WebSocket 消息的相关数据填入事件。
        pictureEditEvent.setSession(session);
        pictureEditEvent.setPictureEditRequestMessage(pictureEditRequestMessage);
        pictureEditEvent.setUser(user);
        pictureEditEvent.setPictureId(pictureId);
        // 提交事件，通知消费者可以读取。
        ringBuffer.publish(next);
        /*
        这里是异步处理,不会阻塞 I/O 线程
        标记事件就绪，消费者工作线程通过自旋检查发现后自动处理
        因为 Disruptor 用的是 Busy Spin（忙等） 模式，而不是 LockSupport.park/unpark 这种主动唤醒。消费者线程一直在类似这样跑：
        忙等虽然会消耗一点 CPU，但避免了线程上下文切换的开销，这就是为什么 Disruptor 比 ArrayBlockingQueue 快——用 CPU 换延迟。
        工作线程会从 ringBuffer 中取事件,自动并调用事件处理方法onEvent处理

        
         */
    }

    /**
     * 优雅停机,等待所有事件处理完成在结束,类似idea里点击关闭项目与任务管理器关闭进程区别
     */
    @PreDestroy
    public void close() {

        pictureEditEventDisruptor.shutdown();

    }
}