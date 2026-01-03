package com.flashform.core;

import com.flashform.core.dto.SubmissionRequest;
import com.flashform.core.service.SeckillService;
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

@SpringBootTest
public class SeckillIntegrationTest {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 定義測試用的表單 ID
    private final String FORM_ID = "101";

    @BeforeEach
    public void setup() {
        // 1. 重置 Redis 環境
        // 設定名額為 10
        redisTemplate.opsForValue().set("form:quota:" + FORM_ID, "10");
        // 清空該表單的購買名單，確保測試公平
        redisTemplate.delete("form:submitted:" + FORM_ID);

        System.out.println("✅ [Test Setup] 環境重置完成，名額: 10");
    }

    @Test
    public void testHighConcurrency() throws InterruptedException {
        // 模擬 1000 人同時搶購
        int peopleCount = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(peopleCount);

        // 發令槍：確保所有執行緒準備好後才視為結束
        CountDownLatch latch = new CountDownLatch(peopleCount);

        // 成功計數器 (Thread-Safe)
        AtomicInteger successCount = new AtomicInteger(0);

        System.out.println("🔥 [Test Start] 1000 人準備開搶...");

        for (int i = 0; i < peopleCount; i++) {
            final String userId = "User_" + i;

            executorService.submit(() -> {
                try {
                    // 準備請求物件
                    SubmissionRequest request = new SubmissionRequest();
                    request.setFormId(FORM_ID);
                    request.setUserId(userId);
                    request.setAnswers(new HashMap<>());

                    // 執行核心邏輯 (Redis 扣減 + RabbitMQ 發送)
                    Long result = seckillService.executeSubmission(request);

                    // 判斷是否搶購成功 (使用 Objects.equals 避免 null pointer)
                    if (Objects.equals(result, 1L)) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // 任務完成，倒數 -1
                    latch.countDown();
                }
            });
        }

        // 等待 1000 個請求全部執行完畢
        latch.await();

        System.out.println("🏁 [Test Finish] 搶購結束！");
        System.out.println("實際成功人數: " + successCount.get());

        // 驗證結果：名額只有 10 個，所以成功人數必須嚴格等於 10
        assertEquals(10, successCount.get());

        // 🔥 關鍵修改：等待 Consumer 處理
        // 因為 RabbitMQ 是非同步的，主程式跑完 assertions 時，Consumer 可能還在收信。
        // 這裡強制睡 3 秒，讓 Console 有機會印出 Consumer 的 Log。
        System.out.println("⏳ 測試通過，正在等待 Consumer 消化 RabbitMQ 訊息 (3秒)...");
        Thread.sleep(3000);

        executorService.shutdown();
    }
}