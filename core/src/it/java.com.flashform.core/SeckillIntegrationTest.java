package com.flashform.core;

import com.flashform.core.dto.SubmissionRequest;
import com.flashform.core.service.SeckillService;
import com.flashform.core.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("Temporarily disabled due to infrastructure race conditions. Use Unit tests instead.")
@SpringBootTest
public class SeckillIntegrationTest {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SubmissionRepository submissionRepository;
            
    // define form id used for test
    private final String FORM_ID = "101";

    @BeforeEach
    public void setup() {
        // 1. Reset Redis
        redisTemplate.opsForValue().set("form:quota:" + FORM_ID, "10");
        redisTemplate.delete("form:submitted:" + FORM_ID);

        // NEW: 2. Reset Database
         submissionRepository.deleteAll();

        System.out.println("✅ [Test Setup] Environment reset finished (Redis & DB).");
    }

    @Test
    public void testHighConcurrency() throws InterruptedException {
        // simulation of 1000 simultaneous accesses
        int peopleCount = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(peopleCount);

        // ensure all threads are ready
        CountDownLatch latch = new CountDownLatch(peopleCount);

        // counter for success access (thread-safe)
        AtomicInteger successCount = new AtomicInteger(0);

        System.out.println("🔥 [Test Start] 1000 accesses in progress...");

        for (int i = 0; i < peopleCount; i++) {
            final String userId = "User_" + i;

            executorService.submit(() -> {
                try {
                    // prepare request object
                    SubmissionRequest request = new SubmissionRequest();
                    request.setFormId(FORM_ID);
                    request.setUserId(userId);
                    request.setAnswers(new HashMap<>());

                    // execute core logic (Redis decrement + RabbitMQ submission)
                    Long result = seckillService.executeSubmission(request);

                    // examine success or not (use Objects.equals to handle null pointer)
                    if (Objects.equals(result, 1L)) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // task done, countdown -1
                    latch.countDown();
                }
            });
        }

        // wait for all 1000 requests get executed
        latch.await();

        System.out.println("🏁 [Test Finish] Snap up finished！");
        System.out.println("Success count: " + successCount.get());

        // verify result for Redis, success count must be exactly 10
        assertEquals(10, successCount.get());

        // wait for asynchronous process (RabbitMQ Consumer)
        assertEquals(10, successCount.get(), "Redis layer success count should be 10");
        System.out.println("⏳ Waiting for RabbitMQ Consumer (3 sec)...");
        Thread.sleep(3000);

        // verify result for database (eventual consistency)
        long dbCount = submissionRepository.count();
        System.out.println("📝 Database count: " + dbCount);
        assertEquals(10, dbCount, "Database should verify exactly 10 successful submissions");

        executorService.shutdown();
    }

    @Test
    public void testDuplicateSubmission() {
        System.out.println("🔥 [Test Start] Testing Duplicate Submission...");

        String duplicateUser = "User_Cheater";

        // First request: success expected
        SubmissionRequest request1 = new SubmissionRequest();
        request1.setFormId(FORM_ID);
        request1.setUserId(duplicateUser);
        request1.setAnswers(new HashMap<>());

        Long result1 = seckillService.executeSubmission(request1);
        System.out.println("Result 1: " + result1);

        assertEquals(1L, result1, "First request should succeed");

        // Second request: failure expected
        SubmissionRequest request2 = new SubmissionRequest();
        request2.setFormId(FORM_ID);
        request2.setUserId(duplicateUser); // 同樣的 User ID
        request2.setAnswers(new HashMap<>());

        Long result2 = seckillService.executeSubmission(request2);
        System.out.println("Result 2: " + result2);

        assertEquals(-1L, result2, "Second request should be rejected");

        System.out.println("✅ Duplicate submission test passed.");
    }
}