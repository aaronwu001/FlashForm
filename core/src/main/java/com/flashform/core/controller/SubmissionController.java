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
     * user submission
     * POST /api/forms/{formId}/submit
     */
    @PostMapping("/{formId}/submit")
    public Result<String> submitForm(@PathVariable Long formId, @RequestBody SubmissionRequest request) {
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
     * get all submissions for a specific form (Owner only)
     * GET /api/forms/{formId}/submissions
     */
    @GetMapping("/{formId}/submissions")
    public Result<List<Submission>> getSubmissions(@PathVariable Long formId) {
        List<Submission> list = submissionRepository.findByFormId(formId);
        return Result.success("Fetched submissions", list);
    }
}