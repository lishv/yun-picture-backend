package com.xiaoli.yunpicture.infrastructure.manager.email;

import com.xiaoli.yunpicture.infrastructure.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailManagerTest {

    private static final String EMAIL = "user@example.com";
    private static final String BIZ = "register";
    private static final String CODE = "012345";
    private static final String CODE_KEY = "user:code:register:user@example.com";

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private EmailManager emailManager;

    @BeforeEach
    void setUp() {
        emailManager = new EmailManager();
        ReflectionTestUtils.setField(emailManager, "stringRedisTemplate", stringRedisTemplate);
    }

    @Test
    void shouldVerifyAndDeleteCodeInOneRedisScript() {
        when(stringRedisTemplate.execute(any(RedisScript.class),
                eq(Collections.singletonList(CODE_KEY)), eq(CODE))).thenReturn(1L);

        emailManager.verifyCode(EMAIL, BIZ, CODE);

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(stringRedisTemplate).execute(scriptCaptor.capture(),
                eq(Collections.singletonList(CODE_KEY)), eq(CODE));
        String script = scriptCaptor.getValue().getScriptAsString();
        assertTrue(script.contains("redis.call('GET', KEYS[1])"));
        assertTrue(script.contains("redis.call('DEL', KEYS[1])"));
    }

    @Test
    void shouldKeepCodeWhenInputDoesNotMatch() {
        when(stringRedisTemplate.execute(any(RedisScript.class),
                eq(Collections.singletonList(CODE_KEY)), eq(CODE))).thenReturn(0L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> emailManager.verifyCode(EMAIL, BIZ, CODE));

        assertEquals("验证码错误", exception.getMessage());
    }

    @Test
    void shouldReportExpiredCodeWhenCodeDoesNotExist() {
        when(stringRedisTemplate.execute(any(RedisScript.class),
                eq(Collections.singletonList(CODE_KEY)), eq(CODE))).thenReturn(-1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> emailManager.verifyCode(EMAIL, BIZ, CODE));

        assertEquals("验证码已过期", exception.getMessage());
    }
}
