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

    @PostMapping("/reset/{formId}/{quota}")
    public String resetForm(@PathVariable String formId, @PathVariable int quota) {
        redisTemplate.opsForValue().set("form:quota:" + formId, String.valueOf(quota));
        redisTemplate.delete("form:submitted:" + formId);
        return "✅ Form " + formId + " reset successful! Quota set to: " + quota;
    }

    @PostMapping("/submit")
    public String submitForm(@RequestBody SubmissionRequest request) {
        // 🔥 現在這裡不需要任何 try-catch 了！乾乾淨淨
        Long result = seckillService.executeSubmission(request);

        if (result == 1) {
            return "🎉 Submission Successful! (ID: " + request.getUserId() + ")";
        } else if (result == -1) {
            return "⛔ Repeated Submission!";
        } else if (result == 0) {
            return "😭 Quota full.";
        } else {
            return "⚠️ System Error (Unknown Result)";
        }
    }

    @GetMapping("/check/{formId}")
    public String checkQuota(@PathVariable String formId) {
        String quota = redisTemplate.opsForValue().get("form:quota:" + formId);
        return "📦 Form " + formId + " remaining quota: " + (quota != null ? quota : "0");
    }
}