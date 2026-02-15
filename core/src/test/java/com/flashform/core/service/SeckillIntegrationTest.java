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

import java.time.ZoneOffset;
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
        // Clean up stale data in Redis and DB
        redisTemplate.keys("form:*").forEach(redisTemplate::delete);
        formRepository.deleteAll();

        // Initialize a test form in the database
        // Use UTC time to ensure consistency with Service layer logic
        Form form = new Form(
                "owner123",
                "Flash Sale iPhone 16",
                null,
                10,   // Quota
                java.time.LocalDateTime.now(ZoneOffset.UTC).minusHours(1), // Started 1 hour ago
                java.time.LocalDateTime.now(ZoneOffset.UTC).plusHours(1)   // Ends 1 hour later
        );
        form = formRepository.save(form);
        this.formId = form.getId();

        // Note: Redis is left empty to test cache rebuild and breakdown protection
    }

    @Test
    @DisplayName("Test 1: Cache Breakdown Protection")
    public void testCacheBreakdown() throws InterruptedException {
        // Scenario: Redis is empty. 50 threads request simultaneously.
        // Expected: Only 1 thread hits the DB to rebuild cache; others wait and fetch from Redis.

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

        latch.await(); // Wait for all threads to complete

        // 1. Verify cache rebuild
        Boolean hasMeta = redisTemplate.hasKey("form:meta:" + formId);
        Boolean hasQuota = redisTemplate.hasKey("form:quota:" + formId);

        assertTrue(hasMeta, "Meta data should be rebuilt in Redis");
        assertTrue(hasQuota, "Quota should be rebuilt in Redis");

        // 2. Verify quota management (10 spots for 50 requests)
        String remainingQuota = redisTemplate.opsForValue().get("form:quota:" + formId);

        // Lua script ensures quota does not drop below 0
        assertEquals("0", remainingQuota, "Quota should ensure no overselling (min 0)");
        assertEquals(10, successCount.get(), "Should count exactly 10 successes");
    }

    @Test
    @DisplayName("Test 2: Overselling Protection via Lua Script")
    public void testOverselling() throws InterruptedException {
        // Scenario: Pre-warm Redis and simulate 1000 users competing for 10 spots
        redisTemplate.opsForValue().set("form:quota:" + formId, "10");
        Map<String, String> meta = new HashMap<>();
        meta.put("startTime", String.valueOf(System.currentTimeMillis() - 10000));
        meta.put("endTime", String.valueOf(System.currentTimeMillis() + 10000));
        redisTemplate.opsForHash().putAll("form:meta:" + formId, meta);

        int threadCount = 1000;
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

        // Verify that only 10 users succeeded
        assertEquals(10, successCount.get(), "Only 10 users should succeed");

        // Redis quota should be exactly 0
        String stock = redisTemplate.opsForValue().get("form:quota:" + formId);
        assertEquals("0", stock);

        // Verify unique submission set size in Redis
        Long exactWinners = redisTemplate.opsForSet().size("form:submitted:" + formId);
        assertEquals(10L, exactWinners);
    }

    @Test
    @DisplayName("Test 3: Idempotency (Same user cannot submit twice)")
    public void testIdempotency() {
        // Pre-warm Redis cache
        redisTemplate.opsForValue().set("form:quota:" + formId, "5");
        Map<String, String> meta = new HashMap<>();
        meta.put("startTime", String.valueOf(System.currentTimeMillis() - 10000));
        meta.put("endTime", String.valueOf(System.currentTimeMillis() + 10000));
        redisTemplate.opsForHash().putAll("form:meta:" + formId, meta);

        String userId = "greedy_user";
        SubmissionRequest request = new SubmissionRequest();
        request.setFormId(formId);
        request.setUserId(userId);

        // First attempt: Expected success
        Long result1 = seckillService.executeSubmission(request);
        assertEquals(1L, result1, "First attempt should succeed");

        // Second attempt with same User ID: Expected failure
        Long result2 = seckillService.executeSubmission(request);
        assertEquals(-1L, result2, "Second attempt should return -1 (Duplicate)");

        // Quota should only be deducted once
        String stock = redisTemplate.opsForValue().get("form:quota:" + formId);
        assertEquals("4", stock);
    }
}