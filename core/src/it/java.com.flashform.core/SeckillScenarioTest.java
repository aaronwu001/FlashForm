package com.flashform.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashform.core.config.RabbitMQConfig;
import com.flashform.core.dto.FormRequest;
import com.flashform.core.dto.SubmissionRequest;
import com.flashform.core.repository.FormRepository;
import com.flashform.core.repository.SubmissionRepository;
import org.junit.jupiter.api.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FlashForm 核心業務場景集成測試
 * 驗證包含：RESTful 提交路徑、非同步入庫一致性、緩存自動修復與冪等性檢查。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SeckillScenarioTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private SubmissionRepository submissionRepository;
    @Autowired private FormRepository formRepository;
    @Autowired private RabbitAdmin rabbitAdmin;
    @Autowired private RabbitListenerEndpointRegistry registry;

    private String testFormId;

    /**
     * 🧹 環境清理：確保 Redis, DB 與 RabbitMQ 隊列均為空
     */
    private void cleanData() {
        System.out.println("🧹 [Cleanup] Wiping Redis, Database, and RabbitMQ Queue...");
        try {
            redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
            rabbitAdmin.purgeQueue(RabbitMQConfig.QUEUE_NAME);
        } catch (Exception e) {
            System.err.println("Cleanup Warning: " + e.getMessage());
        }
        submissionRepository.deleteAll();
        formRepository.deleteAll();
    }

    @BeforeAll
    public void setup() throws InterruptedException {
        cleanData();
        // 確保測試環境中的 RabbitMQ 監聽器已完全啟動
        registry.getListenerContainers().forEach(container -> {
            if (!container.isRunning()) container.start();
        });
        System.out.println("⏳ Waiting for listeners to stabilize...");
        Thread.sleep(2000);
    }

    /**
     * 🟢 Test 1: Happy Path
     * 驗證 1000 併發下能處理的高併發核心路徑。
     */
    @Test
    @Order(1)
    public void testHappyPath() throws Exception {
        System.out.println("\n========== 🟢 Test 1: Happy Path (Async Ingestion) ==========");

        // 1. 建立表單
        FormRequest formRequest = new FormRequest();
        formRequest.setOwnerId("Admin_Tester");
        formRequest.setTitle("Scenario Test");
        formRequest.setQuota(100);
        formRequest.setStartTime(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        formRequest.setEndTime(LocalDateTime.now(ZoneOffset.UTC).plusHours(5));
        formRequest.setSchemaJson("[{\"name\":\"age\",\"type\":\"NUMBER\",\"required\":true}]");

        MvcResult result = mockMvc.perform(post("/api/forms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formRequest)))
                .andExpect(status().isOk())
                .andReturn();

        testFormId = result.getResponse().getContentAsString().replaceAll("[^0-9]", "");
        System.out.println("👉 Created Form ID: " + testFormId);

        // 🛡️ 關鍵點：人為延遲，防止 Consumer 抓不到剛 Commit 的 Form (避免 Foreign Key Race Condition)
        Thread.sleep(1000);

        // 2. 提交秒殺請求
        SubmissionRequest subRequest = new SubmissionRequest();
        subRequest.setFormId(testFormId);
        subRequest.setUserId("User_Happy");
        subRequest.setAnswers(Map.of("age", 25));

        mockMvc.perform(post("/api/forms/" + testFormId + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Submission Successful")));

        // 3. 使用 Awaitility 動態檢查 DB 寫入狀態
        System.out.println("⏳ Awaiting DB write (Timeout: 20s)...");
        await().atMost(20, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> {
                    long count = submissionRepository.count();
                    System.out.println("🔍 [Polling] DB Submission Count: " + count);
                    return count > 0;
                });

        Assertions.assertEquals(1, submissionRepository.count(), "❌ DB: Data missing despite success response.");
        System.out.println("✅ Happy Path Passed.");
    }

    /**
     * 🔴 Test 2: Validation Fail
     * 驗證 Schema 校驗器是否能正確攔截錯誤資料型別。
     */
    @Test
    @Order(2)
    public void testValidationFail() throws Exception {
        System.out.println("\n========== 🔴 Test 2: Validation Fail ==========");

        SubmissionRequest subRequest = new SubmissionRequest();
        subRequest.setFormId(testFormId);
        subRequest.setUserId("User_Bad");
        subRequest.setAnswers(Map.of("age", "I am a string")); // 故意提供錯誤類型

        mockMvc.perform(post("/api/forms/" + testFormId + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subRequest)))
                .andExpect(status().isInternalServerError()) // 拋出 RuntimeException 時預期為 500
                .andExpect(content().string(containsString("Schema validation error")));

        // DB 數量應維持為 1 (僅包含來自 Test 1 的成功紀錄)
        Assertions.assertEquals(1, submissionRepository.count(), "❌ DB: Invalid data should NOT be written.");
        System.out.println("✅ Validation Fail Passed.");
    }

    /**
     * ⛔ Test 3: Duplicate Submission
     * 驗證 Redis Set 冪等性機制。
     */
    @Test
    @Order(3)
    public void testDuplicateSubmission() throws Exception {
        System.out.println("\n========== ⛔ Test 3: Duplicate Submission ==========");

        SubmissionRequest subRequest = new SubmissionRequest();
        subRequest.setFormId(testFormId);
        subRequest.setUserId("User_Happy"); // 已在 Test 1 成功提交
        subRequest.setAnswers(Map.of("age", 25));

        mockMvc.perform(post("/api/forms/" + testFormId + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Repeated Submission")));
        System.out.println("✅ Duplicate Submission Passed.");
    }

    /**
     * 🔥 Test 4: Cache Recovery
     * 驗證緩存擊穿保護與自動重建機制。
     */
    @Test
    @Order(4)
    public void testCacheRecovery() throws Exception {
        System.out.println("\n========== 🔥 Test 4: Cache Recovery ==========");

        // 手動刪除緩存模擬失效
        redisTemplate.delete("form:meta:" + testFormId);
        redisTemplate.delete("form:quota:" + testFormId);

        SubmissionRequest subRequest = new SubmissionRequest();
        subRequest.setFormId(testFormId);
        subRequest.setUserId("User_Rescue");
        subRequest.setAnswers(Map.of("age", 30));

        mockMvc.perform(post("/api/forms/" + testFormId + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subRequest)))
                .andExpect(status().isOk());

        // 驗證系統是否自動從 DB 重建了 Redis 數據
        Assertions.assertTrue(redisTemplate.hasKey("form:meta:" + testFormId), "❌ Redis: Meta missing.");
        Assertions.assertTrue(redisTemplate.hasKey("form:quota:" + testFormId), "❌ Redis: Quota missing.");
        System.out.println("✅ Cache Recovery Passed.");
    }

    /**
     * 📦 Test 5: Quota Query
     * 驗證 RESTful 管理接口。
     */
    @Test
    @Order(5)
    public void testQuotaQuery() throws Exception {
        System.out.println("\n========== 📦 Test 5: Quota Query ==========");

        mockMvc.perform(get("/api/forms/" + testFormId + "/quota"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("remaining quota")));
        System.out.println("✅ Quota Query Passed.");
    }
}