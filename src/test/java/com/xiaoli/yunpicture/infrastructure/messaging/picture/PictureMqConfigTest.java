package com.xiaoli.yunpicture.infrastructure.messaging.picture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PictureMqConfigTest {

    @Mock
    private PictureCleanupTaskService taskService;

    @Mock
    private PictureDelayPublisher delayPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MessageRecoverer recoverer;

    @BeforeEach
    void setUp() {
        recoverer = new PictureMqConfig().pictureMessageRecoverer(
                objectMapper,
                taskService,
                delayPublisher
        );
    }

    @Test
    void shouldPublishNormalQueueFailureWithNativeDelay() throws Exception {
        RuntimeException cause = new RuntimeException("COS unavailable");

        recoverer.recover(normalQueueMessage(200L), cause);

        ArgumentCaptor<PictureDeleteMessage> messageCaptor =
                ArgumentCaptor.forClass(PictureDeleteMessage.class);
        verify(delayPublisher).publish(messageCaptor.capture());
        assertEquals(200L, messageCaptor.getValue().getTaskId());
        verify(taskService).markWaitingRetry(200L, cause);
    }

    @Test
    void shouldRequeueOriginalMessageWhenNativeDelayPublishFails() throws Exception {
        doThrow(new IllegalStateException("publish failed"))
                .when(delayPublisher)
                .publish(any(PictureDeleteMessage.class));

        assertThrows(
                ImmediateRequeueAmqpException.class,
                () -> recoverer.recover(
                        normalQueueMessage(201L),
                        new RuntimeException("COS unavailable")
                )
        );

        verify(taskService, never()).markWaitingRetry(any(), any());
    }

    private Message normalQueueMessage(Long taskId) throws Exception {
        PictureDeleteMessage deleteMessage = new PictureDeleteMessage();
        deleteMessage.setTaskId(taskId);

        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue(MQPictureConstants.NORMAL_Picture_QUEUE_NAME);
        return new Message(objectMapper.writeValueAsBytes(deleteMessage), properties);
    }
}
