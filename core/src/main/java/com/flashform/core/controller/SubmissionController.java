package com.flashform.core.controller;

import com.flashform.core.dto.Result;
import com.flashform.core.dto.SubmissionRequest;
import com.flashform.core.entity.Submission;
import com.flashform.core.repository.SubmissionRepository;
import com.flashform.core.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/forms")
public class SubmissionController {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private SubmissionRepository submissionRepository;

    /**
     * 用戶提交表單 (秒殺入口)
     * POST /api/forms/{formId}/submit
     */
    @PostMapping("/{formId}/submit")
    // ✨ 修改點：@PathVariable Long formId
    public Result<String> submitForm(@PathVariable Long formId, @RequestBody SubmissionRequest request) {
        // 將路徑上的 formId 塞入 request 物件，確保一致
        request.setFormId(formId);

        Long result = seckillService.executeSubmission(request);

        return switch (result.intValue()) {
            case 1 -> Result.success("🎉 Submission Successful!", request.getUserId());
            case -1 -> Result.error(-1, "⛔ Repeated Submission!");
            case 0 -> Result.error(0, "😭 Quota full.");
            case -2 -> Result.error(-2, "❌ Form Not Found.");
            default -> Result.error(500, "⚠️ System Error");
        };
    }

    /**
     * 查詢某個表單的所有提交紀錄 (Owner 專用)
     * GET /api/forms/{formId}/submissions
     */
    @GetMapping("/{formId}/submissions")
    // ✨ 修改點：@PathVariable Long formId
    public Result<List<Submission>> getSubmissions(@PathVariable Long formId) {
        List<Submission> list = submissionRepository.findByFormId(formId);
        return Result.success("Fetched submissions", list);
    }
}