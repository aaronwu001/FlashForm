package com.flashform.core.service;

import com.flashform.core.dto.SubmissionRequest;
import com.flashform.core.entity.Form;
import com.flashform.core.repository.FormRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class SeckillIntegrationTest {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private FormRepository formRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Long formId;

    @BeforeEach
    public void setup() {
        // 1. clean up old data
        redisTemplate.keys("form:*").forEach(redisTemplate::delete);
        formRepository.deleteAll();

        // 2. prepare test data (one row in DB)
        // use LocalDateTime.now(ZoneOffset.UTC) to ensure logic alignment with time in Service layer
        Form form = new Form(
                "owner123",
                "Flash Sale iPhone 16",
                null, // schema
                10,   // Quota
                java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(1), // started an hour ago
                java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusHours(1)   // ends an hour later
        );
        form = formRepository.save(form);
        this.formId = form.getId();

        // 注意：這裡我們保持 Redis 是空的，以測試緩存擊穿保護機制
    }

    @Test
    @DisplayName("測試 1: 緩存擊穿保護 (Cache Breakdown Protection)")
    public void testCacheBreakdown() throws InterruptedException {
        // 場景：Redis 裡面完全沒有這個 Form 的 Meta 和 Quota
        // 我們模擬 50 個執行緒同時發起請求，看 DB 會不會被擊穿
        // 預期：只有 1 個執行緒去 DB 搬運，其他 49 個等待後從 Redis 讀取

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            String userId = "user_" + i;
            executor.submit(() -> {
                try {
                    SubmissionRequest request = new SubmissionRequest();
                    request.setFormId(formId);
                    request.setUserId(userId);
                    request.setAnswers(new HashMap<>());

                    // 執行秒殺
                    Long result = seckillService.executeSubmission(request);
                    if (result == 1L) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 等待所有執行緒跑完
        latch.await(); // 等待所有執行緒跑完

        // 驗證：
        // 1. 雖然 Redis 一開始是空的，但現在應該要有資料了
        Boolean hasMeta = redisTemplate.hasKey("form:meta:" + formId);
        Boolean hasQuota = redisTemplate.hasKey("form:quota:" + formId);

        assertTrue(hasMeta, "Meta data should be rebuilt in Redis");
        assertTrue(hasQuota, "Quota should be rebuilt in Redis");

        // 2. 庫存應該被扣減 (50 個人搶 10 個，應該賣光)
        String remainingQuota = redisTemplate.opsForValue().get("form:quota:" + formId);

        // ❌ 原本錯誤的寫法：assertEquals("-40", remainingQuota);
        // ✅ 修正後的寫法：Lua 腳本保護了庫存不變負數
        assertEquals("0", remainingQuota, "Quota should ensure no overselling (min 0)");

        // 驗證成功人數確實只有 10 人
        assertEquals(10, successCount.get(), "Should define exactly 10 successes");
    }

    @Test
    @DisplayName("測試 2: Lua 腳本防止超賣 (Overselling Protection)")
    public void testOverselling() throws InterruptedException {
        // 場景：先手動預熱 Redis，確保 1000 人同時搶 10 個名額
        redisTemplate.opsForValue().set("form:quota:" + formId, "10");
        Map<String, String> meta = new HashMap<>();
        meta.put("startTime", String.valueOf(System.currentTimeMillis() - 10000));
        meta.put("endTime", String.valueOf(System.currentTimeMillis() + 10000));
        redisTemplate.opsForHash().putAll("form:meta:" + formId, meta);

        int threadCount = 1000; // 1000 人搶 10 個
        ExecutorService executor = Executors.newFixedThreadPool(200);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            String userId = "user_" + i;
            executor.submit(() -> {
                try {
                    SubmissionRequest request = new SubmissionRequest();
                    request.setFormId(formId);
                    request.setUserId(userId);
                    Long result = seckillService.executeSubmission(request);
                    if (result == 1L) successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // 驗證：
        // 1. 成功人數必須嚴格等於 10
        assertEquals(10, successCount.get(), "Only 10 users should succeed");

        // 2. Redis 庫存必須是 0
        String stock = redisTemplate.opsForValue().get("form:quota:" + formId);
        assertEquals("0", stock);

        // 3. Set 裡面應該只有 10 個人
        Long exactWinners = redisTemplate.opsForSet().size("form:submitted:" + formId);
        assertEquals(10L, exactWinners);
    }

    @Test
    @DisplayName("測試 3: 冪等性 (同一個人不能買兩次)")
    public void testIdempotency() {
        // 手動預熱
        redisTemplate.opsForValue().set("form:quota:" + formId, "5");
        Map<String, String> meta = new HashMap<>();
        meta.put("startTime", String.valueOf(System.currentTimeMillis() - 10000));
        meta.put("endTime", String.valueOf(System.currentTimeMillis() + 10000));
        redisTemplate.opsForHash().putAll("form:meta:" + formId, meta);

        String userId = "greedy_user";
        SubmissionRequest request = new SubmissionRequest();
        request.setFormId(formId);
        request.setUserId(userId);

        // 第一次購買
        Long result1 = seckillService.executeSubmission(request);
        assertEquals(1L, result1, "First attempt should succeed");

        // 第二次購買 (同一個 user)
        Long result2 = seckillService.executeSubmission(request);
        assertEquals(-1L, result2, "Second attempt should return -1 (Repeated)");

        // 檢查庫存：應該只扣了 1，而不是 2
        String stock = redisTemplate.opsForValue().get("form:quota:" + formId);
        assertEquals("4", stock);
    }
}