package com.xiaoli.yunpicture.infrastructure.messaging.picture;

import com.xiaoli.yunpicture.domain.picture.service.PictureDomainService;
import com.xiaoli.yunpicture.infrastructure.manager.email.EmailManager;
import com.xiaoli.yunpicture.infrastructure.mapper.PictureCleanupTaskMapper;
import com.xiaoli.yunpicture.infrastructure.mapper.PictureMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PictureDeleteListenerTest {

    private static final String ALERT_EMAIL = "2570410870@qq.com";
    private static final String COOLDOWN_KEY = "picture:cleanup:admin-email:cooldown";

    @Mock
    private PictureCleanupTaskMapper pictureCleanupTaskMapper;

    @Mock
    private PictureMapper pictureMapper;

    @Mock
    private PictureDomainService pictureDomainService;

    @Mock
    private EmailManager emailManager;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private PictureDeleteListener listener;

    @BeforeEach
    void setUp() {
        listener = new PictureDeleteListener(
                pictureCleanupTaskMapper,
                pictureMapper,
                pictureDomainService,
                emailManager,
                stringRedisTemplate
        );
        ReflectionTestUtils.setField(listener, "cleanupAlertEmail", ALERT_EMAIL);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldSendAlertForFirstDeadLetter() {
        PictureDeleteMessage message = message(1L);
        when(valueOperations.setIfAbsent(COOLDOWN_KEY, "1", 30, TimeUnit.MINUTES))
                .thenReturn(true);

        listener.consumeDeadLetter(message);

        verify(emailManager).sendPictureCleanupFailureAlert(ALERT_EMAIL, 1L);
        verify(stringRedisTemplate, never()).delete(COOLDOWN_KEY);
    }

    @Test
    void shouldAcknowledgeWithoutEmailDuringCooldown() {
        PictureDeleteMessage message = message(2L);
        when(valueOperations.setIfAbsent(COOLDOWN_KEY, "1", 30, TimeUnit.MINUTES))
                .thenReturn(false);

        listener.consumeDeadLetter(message);

        verify(emailManager, never()).sendPictureCleanupFailureAlert(ALERT_EMAIL, 2L);
    }

    @Test
    void shouldReleaseCooldownWhenEmailFails() {
        PictureDeleteMessage message = message(3L);
        when(valueOperations.setIfAbsent(COOLDOWN_KEY, "1", 30, TimeUnit.MINUTES))
                .thenReturn(true);
        doThrow(new RuntimeException("mail failed"))
                .when(emailManager)
                .sendPictureCleanupFailureAlert(ALERT_EMAIL, 3L);

        assertThrows(RuntimeException.class, () -> listener.consumeDeadLetter(message));

        verify(stringRedisTemplate).delete(COOLDOWN_KEY);
    }

    private PictureDeleteMessage message(Long taskId) {
        PictureDeleteMessage message = new PictureDeleteMessage();
        message.setTaskId(taskId);
        return message;
    }
}
