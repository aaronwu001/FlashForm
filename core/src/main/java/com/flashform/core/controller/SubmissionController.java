package com.flashform.core.controller;

import com.flashform.core.dto.SubmissionRequest;
import com.flashform.core.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/forms/{formId}")
public class SubmissionController {

    @Autowired
    private SeckillService seckillService;

    /**
     * ✅ 專業 RESTful 秒殺提交
     * 前端 Body 只需要帶: { "userId": "...", "answers": {...} }
     */
    @PostMapping("/submit")
    public String submitForm(@PathVariable String formId, @RequestBody SubmissionRequest request) {
        // 🛡️ 從路徑變數中獲取 ID 並注入 DTO，確保後續 Service 與 MQ 使用的是正確的 ID
        request.setFormId(formId);

        Long result = seckillService.executeSubmission(request);

        // 返回結果映射
        return switch (result.intValue()) {
            case 1 -> "🎉 Submission Successful! (User: " + request.getUserId() + ")";
            case -1 -> "⛔ Repeated Submission!";
            case 0 -> "😭 Quota full.";
            case -2 -> "❌ Form Not Found.";
            default -> "⚠️ System Error";
        };
    }
}