package com.flashform.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashform.core.dto.FormRequest;
import com.flashform.core.dto.SubmissionRequest;
import com.flashform.core.entity.Submission;
import com.flashform.core.repository.FormRepository;
import com.flashform.core.repository.SubmissionRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.ZoneOffset; // 👈 關鍵：導入 UTC 時區
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SeckillScenarioTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private FormRepository formRepository;

    private String testFormId;

    /**
     * 🧹 環境清理工具
     */
    private void cleanData() {
        try {
            // 清空 Redis
            redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        } catch (Exception e) {
            System.err.println("Redis flush failed: " + e.getMessage());
        }
        // 清空 DB (先刪子表再刪主表)
        submissionRepository.deleteAll();
        formRepository.deleteAll();
    }

    @BeforeAll
    public void setup() {
        System.out.println("\n🧹 [Init] Cleaning up environment...");
        cleanData();
    }

    @AfterAll
    public void teardown() {
        System.out.println("\n🧹 [Cleanup] Cleaning up after tests...");
        cleanData();
        System.out.println("✅ All Tests Finished.");
    }

    /**
     * 🟢 場景一：快樂路徑 (Happy Path) - UTC 標準版
     * 目的：建立表單並測試 Redis -> MQ -> DB 的完整流程。
     */
    @Test
    @Order(1)
    public void testHappyPath() throws Exception {
        System.out.println("\n========== 🟢 測試 1：Happy Path (UTC) ==========");

        // 1. 創建表單
        FormRequest formRequest = new FormRequest();
        formRequest.setOwnerId("Admin_Tester");
        formRequest.setTitle("UTC Standard Test");
        formRequest.setQuota(100);

        // 🔥 關鍵：使用 UTC 時間產生測試資料
        // 這樣傳給後端時，後端會把它當作 UTC 處理，與 Service 層的邏輯一致。
        formRequest.setStartTime(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        formRequest.setEndTime(LocalDateTime.now(ZoneOffset.UTC).plusHours(5));
        formRequest.setSchemaJson("[{\"name\":\"age\",\"type\":\"NUMBER\",\"required\":true}]");

        MvcResult result = mockMvc.perform(post("/api/forms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(formRequest)))
                .andExpect(status().isOk())
                .andReturn();

        // 保存 Form ID 給後面的測試用
        testFormId = result.getResponse().getContentAsString().replaceAll("[^0-9]", "");
        System.out.println("👉 Form ID: " + testFormId);

        // 2. 用戶提交
        Map<String, Object> answers = new HashMap<>();
        answers.put("age", 25);
        String userId = "User_Happy";

        SubmissionRequest subRequest = new SubmissionRequest();
        subRequest.setFormId(testFormId);
        subRequest.setUserId(userId);
        subRequest.setAnswers(answers);

        mockMvc.perform(post("/api/form/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Submission Successful")));

        // 3. 驗證
        TimeUnit.SECONDS.sleep(1); // 等待 MQ 寫入

        // Redis 檢查
        Boolean isMember = redisTemplate.opsForSet().isMember("form:submitted:" + testFormId, userId);
        Assertions.assertTrue(isMember, "❌ Redis: User 應該在 submitted set 中");

        // DB 檢查
        List<Submission> records = submissionRepository.findAll();
        boolean existsInDb = records.stream().anyMatch(s -> s.getUserId().equals(userId));
        Assertions.assertTrue(existsInDb, "❌ DB: 資料應該已寫入 PostgreSQL");

        System.out.println("✅ Happy Path 通過！");
    }

    /**
     * 🔴 場景二：驗證失敗 (Validation Fail)
     * 目的：測試 FormValidator 是否生效，且無效資料不會寫入 DB。
     */
    @Test
    @Order(2)
    public void testValidationFail() throws Exception {
        System.out.println("\n========== 🔴 測試 2：Validation Fail ==========");

        // 故意給錯誤型別 (String instead of Number)
        Map<String, Object> badAnswers = new HashMap<>();
        badAnswers.put("age", "Not a number");
        String userId = "User_Bad";

        SubmissionRequest subRequest = new SubmissionRequest();
        subRequest.setFormId(testFormId);
        subRequest.setUserId(userId);
        subRequest.setAnswers(badAnswers);

        mockMvc.perform(post("/api/form/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("System Error"))); // 對應 -3L

        TimeUnit.MILLISECONDS.sleep(500);

        // 驗證 DB 絕對不能有這個人
        List<Submission> records = submissionRepository.findAll();
        boolean existsInDb = records.stream().anyMatch(s -> s.getUserId().equals(userId));
        Assertions.assertFalse(existsInDb, "❌ DB: 錯誤資料不該寫入資料庫！");

        System.out.println("✅ Validation Fail 測試通過！");
    }

    /**
     * ⛔ 場景三：重複提交 (Duplicate Submission)
     * 目的：測試 Redis 的 Set 是否能擋下重複的 User ID。
     */
    @Test
    @Order(3)
    public void testDuplicateSubmission() throws Exception {
        System.out.println("\n========== ⛔ 測試 3：Duplicate Submission ==========");

        // 使用場景一已經買過的 "User_Happy" 再買一次
        Map<String, Object> answers = new HashMap<>();
        answers.put("age", 25);
        String userId = "User_Happy";

        SubmissionRequest subRequest = new SubmissionRequest();
        subRequest.setFormId(testFormId);
        subRequest.setUserId(userId);
        subRequest.setAnswers(answers);

        MvcResult result = mockMvc.perform(post("/api/form/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        System.out.println("👉 重複購買的回傳: " + response);
        Assertions.assertFalse(response.contains("Submission Successful"), "❌ 應該要擋下重複購買");

        System.out.println("✅ Duplicate Submission 測試通過！");
    }

    /**
     * 🔥 場景四：Meta/Quota 救援 (Cache Rebuild)
     * 目的：刪除 Redis 規則，測試系統能否從 DB 撈回規則並成功下單。
     */
    @Test
    @Order(4)
    public void testMetaAndQuotaRebuild() throws Exception {
        System.out.println("\n========== 🔥 測試 4：Meta & Quota Rebuild ==========");

        // 1. 破壞：刪除 Redis 裡的 Meta 和 Quota
        redisTemplate.delete("form:meta:" + testFormId);
        redisTemplate.delete("form:quota:" + testFormId);
        Assertions.assertFalse(redisTemplate.hasKey("form:meta:" + testFormId), "Meta Key 應已刪除");

        // 2. 救援：新用戶嘗試購買
        String userId = "User_Rescue_Meta";
        Map<String, Object> answers = new HashMap<>();
        answers.put("age", 30);

        SubmissionRequest subRequest = new SubmissionRequest();
        subRequest.setFormId(testFormId);
        subRequest.setUserId(userId);
        subRequest.setAnswers(answers);

        mockMvc.perform(post("/api/form/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Submission Successful")));

        // 3. 驗證：Redis Key 是否重生
        Assertions.assertTrue(redisTemplate.hasKey("form:meta:" + testFormId), "❌ Redis: Meta 應該要被重建");
        Assertions.assertTrue(redisTemplate.hasKey("form:quota:" + testFormId), "❌ Redis: Quota 應該要被重建");

        System.out.println("✅ Meta & Quota Rebuild 測試通過！");
    }

    /**
     * 👻 場景五：幽靈名單救援 (Submitted Set Rebuild / Ghost User)
     * 目的：測試當 Redis 名單遺失時，是否能從 DB 搬回舊名單來擋下重複購買。
     */
    @Test
    @Order(5)
    public void testSubmittedSetRebuild() throws Exception {
        System.out.println("\n========== 👻 測試 5：Ghost User Rebuild ==========");

        // 1. 製造幽靈：直接寫 DB，不經由 Redis
        String ghostUserId = "User_Ghost";
        Submission ghostRecord = new Submission(testFormId, ghostUserId, "{\"age\":99}");
        submissionRepository.save(ghostRecord);

        // 2. 破壞：確保 Redis 裡沒有這個人的紀錄 (清空 Set)
        String submittedKey = "form:submitted:" + testFormId;
        redisTemplate.delete(submittedKey);
        Assertions.assertFalse(redisTemplate.hasKey(submittedKey));

        // 3. 嘗試：幽靈用戶試圖再次購買
        SubmissionRequest subRequest = new SubmissionRequest();
        subRequest.setFormId(testFormId);
        subRequest.setUserId(ghostUserId);
        subRequest.setAnswers(new HashMap<>(Map.of("age", 99)));

        MvcResult result = mockMvc.perform(post("/api/form/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subRequest)))
                .andExpect(status().isOk())
                .andReturn();

        // 4. 驗證結果
        String response = result.getResponse().getContentAsString();
        System.out.println("👉 幽靈用戶回傳: " + response);

        Assertions.assertFalse(response.contains("Submission Successful"), "❌ 失敗：幽靈用戶重複購買成功！(資料未搬運)");

        // 驗證 Redis 是否已重建名單
        Boolean isMember = redisTemplate.opsForSet().isMember(submittedKey, ghostUserId);
        Assertions.assertTrue(isMember, "❌ Redis: Set 應該要包含從 DB 搬回來的 Ghost User");

        System.out.println("✅ Ghost User Rebuild 測試通過！");
    }

    /**
     * 🔥 場景六：部分緩存失效 (Partial Cache Miss - Meta Hit, Quota Miss)
     * 目的：模擬「Meta 還在，但 Quota 被誤刪」的情況。
     * 預期：Service 應該要發現 Quota 遺失，自動從 DB 撈回 Quota。
     */
    @Test
    @Order(6)
    public void testPartialCacheMiss_QuotaMissing() throws Exception {
        System.out.println("\n========== 🔥 測試 6：Partial Cache Miss (Quota Lost) ==========");

        // 1. 確保環境狀態：Meta 必須存在，但我們要手動刪除 Quota
        String metaKey = "form:meta:" + testFormId;
        String quotaKey = "form:quota:" + testFormId;

        if (Boolean.FALSE.equals(redisTemplate.hasKey(metaKey))) {
            System.out.println("⚠️ Meta 不在 (非預期)，但我們專注測試 Quota 消失");
        }

        // 🧨 刪除 Quota
        redisTemplate.delete(quotaKey);
        Assertions.assertFalse(redisTemplate.hasKey(quotaKey), "前置條件失敗：Quota 必須被刪除");

        System.out.println("🧨 已手動刪除 Quota，但保留 Meta");

        // 2. 用戶嘗試購買
        String userId = "User_Partial_Miss";
        Map<String, Object> answers = new HashMap<>();
        answers.put("age", 28);

        SubmissionRequest subRequest = new SubmissionRequest();
        subRequest.setFormId(testFormId);
        subRequest.setUserId(userId);
        subRequest.setAnswers(answers);

        // 3. 發送請求
        MvcResult result = mockMvc.perform(post("/api/form/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Submission Successful")))
                .andReturn();

        System.out.println("👉 回傳結果: " + result.getResponse().getContentAsString());

        // 4. 驗證 Quota 是否被自動救回
        Assertions.assertTrue(redisTemplate.hasKey(quotaKey), "❌ Redis: Quota 應該要被 Service 自動救回來");

        String quotaValue = redisTemplate.opsForValue().get(quotaKey);
        System.out.println("✅ Quota 已恢復，目前剩餘: " + quotaValue);
    }
}