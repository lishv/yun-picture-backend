package com.xiaoli.yunpicture.infrastructure.messaging.picture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.amqp.Message;
import com.rabbitmq.client.amqp.Publisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PictureDelayPublisherTest {

    @Mock
    private Publisher publisher;

    @Mock
    private Message amqpMessage;

    @Mock
    private Publisher.Context context;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PictureDelayPublisher delayPublisher;

    @BeforeEach
    void setUp() {
        delayPublisher = new PictureDelayPublisher(publisher, objectMapper);
        when(publisher.message(any(byte[].class))).thenReturn(amqpMessage);
        when(amqpMessage.durable(true)).thenReturn(amqpMessage);
        when(amqpMessage.contentType("application/json")).thenReturn(amqpMessage);
        when(amqpMessage.annotation(eq("x-opt-delivery-time"), any(Long.class)))
                .thenReturn(amqpMessage);
    }

    @Test
    void shouldPublishNativeDelayedMessageAfterBrokerAcceptsIt() throws Exception {
        when(context.status()).thenReturn(Publisher.Status.ACCEPTED);
        completePublishWith(context);

        PictureDeleteMessage deleteMessage = new PictureDeleteMessage();
        deleteMessage.setTaskId(100L);
        long beforePublish = System.currentTimeMillis();

        delayPublisher.publish(deleteMessage);

        ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(publisher).message(bodyCaptor.capture());
        PictureDeleteMessage published = objectMapper.readValue(
                bodyCaptor.getValue(),
                PictureDeleteMessage.class
        );
        assertEquals(100L, published.getTaskId());

        ArgumentCaptor<Long> deliveryTimeCaptor = ArgumentCaptor.forClass(Long.class);
        verify(amqpMessage).annotation(
                eq("x-opt-delivery-time"),
                deliveryTimeCaptor.capture()
        );
        long deliveryTime = deliveryTimeCaptor.getValue();
        assertTrue(deliveryTime >= beforePublish + 1_800_000L);
        assertTrue(deliveryTime <= System.currentTimeMillis() + 1_800_000L);
        verify(publisher).publish(eq(amqpMessage), any(Publisher.Callback.class));
    }

    @Test
    void shouldFailWhenBrokerDoesNotAcceptDelayedMessage() {
        when(context.status()).thenReturn(Publisher.Status.REJECTED);
        completePublishWith(context);

        PictureDeleteMessage deleteMessage = new PictureDeleteMessage();
        deleteMessage.setTaskId(101L);

        assertThrows(
                IllegalStateException.class,
                () -> delayPublisher.publish(deleteMessage)
        );
    }

    private void completePublishWith(Publisher.Context publishContext) {
        doAnswer(invocation -> {
            Publisher.Callback callback = invocation.getArgument(1);
            callback.handle(publishContext);
            return null;
        }).when(publisher).publish(eq(amqpMessage), any(Publisher.Callback.class));
    }
}
