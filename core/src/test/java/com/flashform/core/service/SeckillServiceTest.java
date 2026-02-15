package com.flashform.core.service;

import com.flashform.core.dto.SubmissionRequest;
import com.flashform.core.entity.Form;
import com.flashform.core.exception.BusinessException;
import com.flashform.core.repository.FormRepository;
import com.flashform.core.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SeckillServiceTest {

    @InjectMocks
    private SeckillService seckillService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private FormRepository formRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private FormValidator formValidator;

    // Mock Redis Operations
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private SetOperations<String, String> setOps;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
    }

    @Test
    void testSeckillSuccess() {
        // Arrange
        Long formId = 1L;
        String userId = "user123";
        SubmissionRequest request = new SubmissionRequest();
        request.setFormId(formId);
        request.setUserId(userId);
        request.setAnswers(new HashMap<>());

        // Mock Redis Meta (Cache Hit)
        Map<Object, Object> meta = new HashMap<>();
        meta.put("startTime", String.valueOf(System.currentTimeMillis() - 1000));
        meta.put("endTime", String.valueOf(System.currentTimeMillis() + 10000));
        meta.put("schema", "[]");
        when(hashOps.entries("form:meta:" + formId)).thenReturn(meta);

        // Mock Quota Check
        when(redisTemplate.hasKey("form:quota:" + formId)).thenReturn(true);
        when(valueOps.decrement("form:quota:" + formId)).thenReturn(99L);

        // Mock Idempotency (User hasn't submitted yet)
        when(redisTemplate.hasKey("form:submitted:" + formId)).thenReturn(true);
        when(setOps.isMember("form:submitted:" + formId, userId)).thenReturn(false);

        // Act
        Long result = seckillService.executeSubmission(request);

        // Assert
        assertEquals(1L, result);
        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), eq(request));
    }

    @Test
    void testSeckillQuotaFull() {
        // Arrange
        Long formId = 1L; // ✨ 修正
        String userId = "user123";
        SubmissionRequest request = new SubmissionRequest();
        request.setFormId(formId); // ✨ 修正
        request.setUserId(userId);
        request.setAnswers(new HashMap<>());

        Map<Object, Object> meta = new HashMap<>();
        meta.put("startTime", String.valueOf(System.currentTimeMillis() - 1000));
        meta.put("endTime", String.valueOf(System.currentTimeMillis() + 10000));
        when(hashOps.entries("form:meta:" + formId)).thenReturn(meta);

        when(redisTemplate.hasKey("form:quota:" + formId)).thenReturn(true);
        when(redisTemplate.hasKey("form:submitted:" + formId)).thenReturn(true);
        when(setOps.isMember("form:submitted:" + formId, userId)).thenReturn(false);

        // Mock Quota Full (Decrement returns -1)
        when(valueOps.decrement("form:quota:" + formId)).thenReturn(-1L);

        // Act
        Long result = seckillService.executeSubmission(request);

        // Assert
        assertEquals(0L, result); // 0 means Quota Full
        // Should increment back the quota
        verify(valueOps, times(1)).increment("form:quota:" + formId);
    }

    @Test
    void testSeckillTimeNotStarted() {
        // Arrange
        Long formId = 1L;
        SubmissionRequest request = new SubmissionRequest();
        request.setFormId(formId);
        request.setUserId("user1");

        Map<Object, Object> meta = new HashMap<>();
        // Start time is in the future
        meta.put("startTime", String.valueOf(System.currentTimeMillis() + 5000));
        meta.put("endTime", String.valueOf(System.currentTimeMillis() + 10000));
        when(hashOps.entries("form:meta:" + formId)).thenReturn(meta);
        when(redisTemplate.hasKey("form:quota:" + formId)).thenReturn(true);

        // Act & Assert
        assertThrows(BusinessException.class, () -> {
            seckillService.executeSubmission(request);
        });
    }
}