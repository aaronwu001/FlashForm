package com.flashform.core.controller;

import com.flashform.core.dto.SubmissionRequest;
import com.flashform.core.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/form")
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 1. Initialize form quota (Admin)
    // Example: POST: http://localhost:8080/api/form/reset/101/50
    @PostMapping("/reset/{formId}/{quota}")
    public String resetForm(@PathVariable String formId, @PathVariable int quota) {
        redisTemplate.opsForValue().set("form:quota:" + formId, String.valueOf(quota));
        redisTemplate.delete("form:submitted:" + formId);
        return "✅ Form " + formId + " reset successful! Quota set to: " + quota;
    }

    // 2. Form Submission (User)
    // Example: POST: http://localhost:8080/api/form/submit
    // Body (JSON): { "formId": "101", "userId": "Aaron", "answers": {"q1": "A"} }
    @PostMapping("/submit")
    public String submitForm(@RequestBody SubmissionRequest request) {
        // call service
        Long result = seckillService.executeSubmission(request);

        if (result == 1) {
            // TODO (Phase 3): 這裡之後要加上 RabbitMQ 發送 request 到 Queue
            return "🎉 Submission Successful! Processing... (ID: " + request.getUserId() + ")";
        } else if (result == -1) {
            return "⛔ " + request.getUserId() + " Repeated Submission!";
        } else if (result == 0) {
            return "😭 Sorry, the quota is full.";
        } else {
            return "⚠️ System Error";
        }
    }

    // 3. Check remaining quota
    @GetMapping("/check/{formId}")
    public String checkQuota(@PathVariable String formId) {
        String quota = redisTemplate.opsForValue().get("form:quota:" + formId);
        return "📦 Form " + formId + " remaining quota: " + (quota != null ? quota : "0");
    }
}