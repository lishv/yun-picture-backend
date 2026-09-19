package com.xiaoli.yunpicture.shared.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.xiaoli.yunpicture.shared.websocket.disruptor.PictureEditEventProducer;
import com.xiaoli.yunpicture.shared.websocket.model.PictureEditActionEnum;
import com.xiaoli.yunpicture.shared.websocket.model.PictureEditMessageTypeEnum;
import com.xiaoli.yunpicture.shared.websocket.model.PictureEditRequestMessage;
import com.xiaoli.yunpicture.shared.websocket.model.PictureEditResponseMessage;
import com.xiaoli.yunpicture.domain.user.entity.User;
import com.xiaoli.yunpicture.application.service.UserApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 图片编辑 WebSocket 处理器
 */
@Slf4j
@Component
public class PictureEditHandler extends TextWebSocketHandler {
    @Resource
    private UserApplicationService userApplicationService;
    @Resource
    @Lazy
    private PictureEditEventProducer  pictureEditEventProducer;

    // 当前正在编辑的用户 ID,    每张图片的编辑状态：key: pictureId, value: 当前正在编辑的用户 ID,并发的 Map
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();
    // 当前编辑过程中已执行的操作,key: pictureId, value: 操作列表,并发的 Map
    private final Map<Long, List<String>> pictureEditActions = new ConcurrentHashMap<>();

    //连接该图片的所有的webSocket会话集合,   保存所有连接的会话：key: pictureId, value: 用户会话集合,并发的 Map
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    /**
     * 连接建立后调用
     *
     * @param session WebSocket 会话
     * @throws Exception 异常信息
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        //1.保存对话到集合中
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        //通知父类:这个 WebSocket 连接已经建立
        super.afterConnectionEstablished(session);

        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());// 初始化集合
        pictureSessions.get(pictureId).add(session);

        // 只给刚建立连接的用户同步当前编辑状态
        sendCurrentEditState(session, pictureId);

        //构造响应消息,发送加入编辑的消息通知
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s加入编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userApplicationService.getUserVO(user));

        // 广播给同一张图片的用户
        broadcastToPicture(pictureId, pictureEditResponseMessage);

    }

    /**
     * 收到前端发送的消息,根据消息类别处理消息
     *
     * @param session WebSocket 会话
     * @param message 文本消息
     * @throws Exception 异常信息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 将消息解析为 PictureEditMessage
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
        // 从消息中获取消息类型,转换为枚举类型
        PictureEditMessageTypeEnum pictureEditMessageTypeEnum = PictureEditMessageTypeEnum.valueOf(pictureEditRequestMessage.getType());

        // 从 Session 属性中获取公共参数
        Map<String, Object> attributes = session.getAttributes();
        User user = (User) attributes.get("user");
        Long pictureId = (Long) attributes.get("pictureId");

        // 发布事件到 Disruptor 缓冲区
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage, session, user, pictureId);
    }

        /**
         * 连接关闭后调用
         * @param session WebSocket 会话
         * @param status 关闭状态
         * @throws Exception 异常信息
         */
        @Override
        public void afterConnectionClosed (WebSocketSession session, CloseStatus status) throws Exception {
            Map<String, Object> attributes = session.getAttributes();
            Long pictureId = (Long) attributes.get("pictureId");
            User user = (User) attributes.get("user");
            // 移除当前用户的编辑状态
            handleExitEditMessage(null, session, user, pictureId);

            // 删除会话
            Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
            if (sessionSet != null) {
                sessionSet.remove(session);
                if (sessionSet.isEmpty()) {
                    pictureSessions.remove(pictureId);
                    pictureEditingUsers.remove(pictureId);
                    pictureEditActions.remove(pictureId);
                }
            }

            // 响应
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
            String message = String.format("%s离开编辑", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userApplicationService.getUserVO(user));
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    /**
     * 处理进入编辑消息
     * @param pictureEditRequestMessage 图片编辑请求消息
     * @param session WebSocket 会话
     * @param user 用户
     * @param pictureId 图片 ID
     * @throws Exception 异常信息
     */
    public void handleEnterEditMessage (PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession
            session, User user, Long pictureId) throws Exception {
        Long oldEditingUserId = pictureEditingUsers.putIfAbsent(pictureId, user.getId());

        if (oldEditingUserId == null) {
            // 新的一轮编辑从空操作记录开始
            pictureEditActions.put(pictureId, new CopyOnWriteArrayList<>());
            // 设置当前用户为编辑用户
            pictureEditingUsers.put(pictureId, user.getId());
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            // 构造响应消息,广播发送加入编辑的消息通知
            String message = String.format("%s开始编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userApplicationService.getUserVO(user));
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }

    /**
     * 处理编辑操作消息
     * @param pictureEditRequestMessage 图片编辑请求消息
     * @param session WebSocket 会话
     * @param user 用户
     * @param pictureId 图片 ID
     * @throws Exception 异常信息
     */
    public void handleEditActionMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        Long editingUserId = pictureEditingUsers.get(pictureId);
        String editAction = pictureEditRequestMessage.getEditAction();
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if (actionEnum == null) {
            log.error("无效的编辑动作");
            return;
        }
        // 确认是当前编辑者
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            // 保存当前编辑过程中的操作，供后加入的用户恢复状态
            pictureEditActions.computeIfAbsent(pictureId, key -> new CopyOnWriteArrayList<>()).add(editAction);

            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            String message = String.format("%s执行%s", user.getUserName(), actionEnum.getText());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setEditAction(editAction);
            pictureEditResponseMessage.setUser(userApplicationService.getUserVO(user));
            // 广播给除了当前客户端之外的其他用户，否则会造成重复编辑,会执行两次操作
            broadcastToPicture(pictureId, pictureEditResponseMessage, session);
        }
    }

    /**
     * 处理退出编辑消息
     * @param pictureEditRequestMessage 图片编辑请求消息
     * @param session WebSocket 会话
     * @param user 用户
     * @param pictureId 图片 ID
     * @throws Exception 异常信息
     */
    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        //正在编辑的用户
        Long editingUserId = pictureEditingUsers.get(pictureId);
        // 确认是否为当前编辑者
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            // 移除当前用户的编辑状态
            pictureEditingUsers.remove(pictureId, editingUserId);
            //pictureEditingUsers.remove(pictureId);
            // 构造响应，发送退出编辑的消息通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("%s退出编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userApplicationService.getUserVO(user));
            //广播给所有客户端说退出编辑
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }
    /**
     * 广播图片编辑已完成并保存
     *
     * @param pictureId 图片 ID
     * @param user      操作保存的用户
     */
    public void notifyPictureEditComplete(Long pictureId, User user) {
        // 移除正在编辑状态与历史操作
        pictureEditingUsers.remove(pictureId);
        pictureEditActions.remove(pictureId);

        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (CollUtil.isNotEmpty(sessionSet)) {
            PictureEditResponseMessage responseMessage = new PictureEditResponseMessage();
            responseMessage.setType(PictureEditMessageTypeEnum.COMPLETE_EDIT.getValue());
            responseMessage.setMessage(String.format("%s已保存并完成编辑", user.getUserName()));
            responseMessage.setUser(userApplicationService.getUserVO(user));
            try {
                broadcastToPicture(pictureId, responseMessage);
            } catch (Exception e) {
                log.error("广播图片编辑完成通知异常, pictureId: {}", pictureId, e);
            }
        }
    }



    /**
         * 广播消息给所有连接的会话,排除的会话,
         * @param pictureId 图片 ID
         * @param pictureEditResponseMessage 图片编辑响应消息
         * @param excludeSession 排除的会话,默认 null
         * @throws Exception 异常信息
         */
        private void broadcastToPicture (Long pictureId, PictureEditResponseMessage
        pictureEditResponseMessage, WebSocketSession excludeSession) throws Exception {
            Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);// 获取所有连接的会话
            if (CollUtil.isNotEmpty(sessionSet)) {
                // 转换为 JSON 字符串
                String message = getFullJson(pictureEditResponseMessage);
                TextMessage textMessage = new TextMessage(message);// 转换为文本消息
                for (WebSocketSession session : sessionSet) {
                    // 排除掉的 session 不发送
                    if (excludeSession != null && excludeSession.equals(session)) {
                        continue;
                    }
                    if (session.isOpen()) {
                        session.sendMessage(textMessage);
                    }
                }
            }
        }

    /**
     * 将图片编辑响应消息转换为 JSON 字符串,防止 Long 类型丢失精度问题
     * @param pictureEditResponseMessage 图片编辑响应消息
     * @return JSON 字符串
     * @throws JsonProcessingException
     */
    private static String getFullJson(PictureEditResponseMessage pictureEditResponseMessage) throws JsonProcessingException {
        // 创建 ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();
        // 配置序列化：将 Long 类型转为 String，解决丢失精度问题
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance); // 支持 long 基本类型
        objectMapper.registerModule(module);
        // message序列化为 JSON 字符串
        String message = objectMapper.writeValueAsString(pictureEditResponseMessage);
        return message;
    }

    /**
         * 广播消息给所有连接的会话,默认不排除任何会话
         * @param pictureId 图片 ID
         * @param pictureEditResponseMessage 图片编辑响应消息
         * @throws Exception 异常信息
         */
        private void broadcastToPicture (Long pictureId, PictureEditResponseMessage pictureEditResponseMessage) throws
        Exception {
            broadcastToPicture(pictureId, pictureEditResponseMessage, null);
        }
    /**
     * 将当前编辑者和已执行的图片操作同步给新连接
     *
     * @param session   新建立的 WebSocket 会话
     * @param pictureId 图片 ID
     */
    private void sendCurrentEditState(WebSocketSession session, Long pictureId) throws Exception {
        Long editingUserId = pictureEditingUsers.get(pictureId);
        User editingUser = null;

        if (editingUserId != null) {
            editingUser = userApplicationService.getUserById(editingUserId);

            // 数据库中已经不存在该用户时，清理失效状态
            if (editingUser == null) {
                pictureEditingUsers.remove(pictureId, editingUserId);
            }
        }

        PictureEditResponseMessage responseMessage = new PictureEditResponseMessage();
        responseMessage.setType(PictureEditMessageTypeEnum.SYNC_STATE.getValue());

        if (editingUser != null) {
            responseMessage.setUser(userApplicationService.getUserVO(editingUser));

            List<String> editActions = pictureEditActions.get(pictureId);
            responseMessage.setEditActions(editActions == null ? Collections.emptyList() : new ArrayList<>(editActions));
        } else {
            responseMessage.setEditActions(Collections.emptyList());
        }
        sendResponseToSession(session, responseMessage);
    }

    /**
     * 向指定 WebSocket 会话发送响应
     *
     * @param session         WebSocket 会话
     * @param responseMessage 响应消息
     */
    private void sendResponseToSession(WebSocketSession session, PictureEditResponseMessage responseMessage) throws Exception {
        if (!session.isOpen()) {return;}
        // 转换为 JSON 字符串
        String message = getFullJson(responseMessage);
        session.sendMessage(new TextMessage(message));
    }



}