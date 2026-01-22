package com.flashform.core.service;

import com.flashform.core.config.RabbitMQConfig;
import com.flashform.core.dto.SubmissionRequest;
import com.flashform.core.entity.Form;
import com.flashform.core.repository.FormRepository;
import com.flashform.core.repository.SubmissionRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SeckillServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private FormRepository formRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private FormValidator formValidator;

    @InjectMocks
    private SeckillService seckillService;

    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private SetOperations<String, String> setOperations;

    @BeforeEach
    void setUp() {
        // 確保每次呼叫 opsFor 系列方法都回傳對應的 Mock
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    @DisplayName("🔴 測試時間未開始：預期拋出 RuntimeException")
    void testExecuteSubmission_NotStarted() {
        // 1. 準備請求
        SubmissionRequest request = new SubmissionRequest();
        request.setFormId("1");
        request.setUserId("user_test");
        request.setAnswers(new HashMap<>());

        // 2. 模擬 Redis 中已存在 Meta 數據，但開始時間在未來 (現在 + 1小時)
        long futureStart = System.currentTimeMillis() + 3600000;
        long futureEnd = System.currentTimeMillis() + 7200000;

        Map<Object, Object> metaMap = new HashMap<>();
        metaMap.put("startTime", String.valueOf(futureStart));
        metaMap.put("endTime", String.valueOf(futureEnd));
        metaMap.put("schema", "");

        // ✨ 關鍵 Mock：確保進入 else 分支且時間校驗失敗
        when(hashOperations.entries("form:meta:1")).thenReturn(metaMap);
        // 模擬配額 Key 存在，防止程式碼跑去查資料庫回傳 -2
        when(redisTemplate.hasKey("form:quota:1")).thenReturn(true);

        // 3. 執行並驗證
        RuntimeException exception = Assertions.assertThrows(RuntimeException.class, () -> {
            seckillService.executeSubmission(request);
        });

        // 驗證錯誤訊息是否包含預期內容
        Assertions.assertTrue(exception.getMessage().contains("Form not started yet"),
                "應該包含 'Form not started yet' 但實際訊息為: " + exception.getMessage());
    }

    @Test
    @DisplayName("🟢 測試成功秒殺路徑")
    void testExecuteSubmission_Success() {
        SubmissionRequest request = new SubmissionRequest();
        request.setFormId("1");
        request.setUserId("user_happy");
        request.setAnswers(new HashMap<>());

        // 模擬時間在範圍內
        Map<Object, Object> metaMap = new HashMap<>();
        metaMap.put("startTime", String.valueOf(System.currentTimeMillis() - 10000));
        metaMap.put("endTime", String.valueOf(System.currentTimeMillis() + 10000));
        metaMap.put("schema", "");

        when(hashOperations.entries("form:meta:1")).thenReturn(metaMap);
        when(redisTemplate.hasKey("form:quota:1")).thenReturn(true);
        when(redisTemplate.hasKey("form:submitted:1")).thenReturn(true);
        when(setOperations.isMember("form:submitted:1", "user_happy")).thenReturn(false);
        when(valueOperations.decrement("form:quota:1")).thenReturn(50L);

        Long result = seckillService.executeSubmission(request);

        Assertions.assertEquals(1L, result);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.QUEUE_NAME), any(SubmissionRequest.class));
    }

    @Test
    @DisplayName("🔴 測試重複提交")
    void testExecuteSubmission_Duplicate() {
        SubmissionRequest request = new SubmissionRequest();
        request.setFormId("1");
        request.setUserId("user_happy");

        Map<Object, Object> metaMap = new HashMap<>();
        metaMap.put("startTime", String.valueOf(System.currentTimeMillis() - 10000));
        metaMap.put("endTime", String.valueOf(System.currentTimeMillis() + 10000));

        when(hashOperations.entries("form:meta:1")).thenReturn(metaMap);
        when(redisTemplate.hasKey("form:quota:1")).thenReturn(true);
        when(redisTemplate.hasKey("form:submitted:1")).thenReturn(true);
        // ✨ 模擬使用者已經提交過
        when(setOperations.isMember("form:submitted:1", "user_happy")).thenReturn(true);

        Long result = seckillService.executeSubmission(request);

        Assertions.assertEquals(-1L, result);
        verify(valueOperations, never()).decrement(anyString());
    }
}