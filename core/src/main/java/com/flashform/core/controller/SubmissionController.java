package com.flashform.core.controller;

import com.flashform.core.dto.SubmissionRequest;
import com.flashform.core.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/forms/{formId}") // 資源路徑化
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    /**
     * 秒殺提交路徑
     * POST /api/forms/{formId}/submit
     */
    @PostMapping("/submit")
    public String submitForm(@PathVariable String formId, @RequestBody SubmissionRequest request) {
        // 🛡️ 安全機制：強制將 URL 中的 formId 注入 Request 物件，防止參數不一致
        request.setFormId(formId);

        // 執行 Service 層邏輯
        Long result = seckillService.executeSubmission(request);

        // 根據 Service 返回的代碼進行翻譯 (這部分維持原樣)
        if (result == 1) {
            return "🎉 Submission Successful! (User: " + request.getUserId() + ")";
        } else if (result == -1) {
            return "⛔ Repeated Submission!";
        } else if (result == 0) {
            return "😭 Quota full.";
        } else if (result == -2) {
            return "❌ Form Not Found.";
        } else {
            return "⚠️ System Error";
        }
    }
}