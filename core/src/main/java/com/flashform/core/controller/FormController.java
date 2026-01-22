package com.flashform.core.controller;

import com.flashform.core.dto.FormRequest;
import com.flashform.core.entity.Form;
import com.flashform.core.repository.FormRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/forms")
public class FormController {

    @Autowired
    private FormRepository formRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 1. 創建表單並進行緩存預熱 (Cache Warm-up)
     * POST /api/forms
     */
    @PostMapping
    public String createForm(@RequestBody FormRequest request) {
        Form form = new Form(
                request.getOwnerId(),
                request.getTitle(),
                request.getSchemaJson(),
                request.getQuota(),
                request.getStartTime(),
                request.getEndTime()
        );

        form = formRepository.save(form);
        String formId = form.getId().toString();

        // 🔥 Redis 預熱：寫入 Quota
        redisTemplate.opsForValue().set("form:quota:" + formId, form.getQuota().toString());

        // 🔥 Redis 預熱：寫入 Meta (使用 UTC 時間戳)
        long startMillis = form.getStartTime().toInstant(ZoneOffset.UTC).toEpochMilli();
        long endMillis = form.getEndTime().toInstant(ZoneOffset.UTC).toEpochMilli();

        Map<String, String> metaData = new HashMap<>();
        metaData.put("startTime", String.valueOf(startMillis));
        metaData.put("endTime", String.valueOf(endMillis));
        metaData.put("schema", form.getSchemaJson() != null ? form.getSchemaJson() : "");

        redisTemplate.opsForHash().putAll("form:meta:" + formId, metaData);

        return "✅ Form created successfully! ID: " + formId;
    }

    /**
     * 2. 查詢剩餘配額 (管理員/用戶均可查看)
     * GET /api/forms/{formId}/quota
     */
    @GetMapping("/{formId}/quota")
    public String getQuota(@PathVariable String formId) {
        String quota = redisTemplate.opsForValue().get("form:quota:" + formId);
        return "📦 Form " + formId + " remaining quota: " + (quota != null ? quota : "0");
    }

    /**
     * 3. 重置表單狀態 (Admin 專用)
     * POST /api/forms/{formId}/reset/{quota}
     */
    @PostMapping("/{formId}/reset/{quota}")
    public String resetQuota(@PathVariable String formId, @PathVariable int quota) {
        // 更新配額
        redisTemplate.opsForValue().set("form:quota:" + formId, String.valueOf(quota));
        // 清除已提交名單 (讓用戶可以重新參加測試)
        redisTemplate.delete("form:submitted:" + formId);

        return "🔄 Form " + formId + " reset successful! New Quota: " + quota;
    }
}