package com.flashform.core.controller;

import com.flashform.core.dto.FormRequest;
import com.flashform.core.dto.Result;
import com.flashform.core.entity.Form;
import com.flashform.core.repository.FormRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/forms")
public class FormController {

    @Autowired
    private FormRepository formRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * Create a new form and perform Cache Warm-up.
     * Persistence: Saves to Database.
     * Performance: Pre-loads quota and metadata into Redis.
     */
    @PostMapping
    public Result<Form> createForm(@RequestBody FormRequest request) {
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

        // Redis Warm-up: Initialize Quota
        redisTemplate.opsForValue().set("form:quota:" + formId, form.getQuota().toString());

        // Redis Warm-up: Initialize Meta (using UTC timestamps)
        long startMillis = form.getStartTime().toInstant(ZoneOffset.UTC).toEpochMilli();
        long endMillis = form.getEndTime().toInstant(ZoneOffset.UTC).toEpochMilli();

        Map<String, String> metaData = new HashMap<>();
        metaData.put("startTime", String.valueOf(startMillis));
        metaData.put("endTime", String.valueOf(endMillis));
        metaData.put("schema", form.getSchemaJson() != null ? form.getSchemaJson() : "");

        redisTemplate.opsForHash().putAll("form:meta:" + formId, metaData);

        // Return Result containing Form object for immediate frontend usage (ID, title, etc.)
        return Result.success("Form Created Successfully", form);
    }

    /**
     * Fetch forms created by a specific user (Owner).
     */
    @GetMapping("/owner/{ownerId}")
    public Result<List<Form>> getMyForms(@PathVariable String ownerId) {
        List<Form> forms = formRepository.findByOwnerId(ownerId);
        return Result.success("Fetched user forms", forms);
    }

    /**
     * Fetch all available public forms.
     */
    @GetMapping("/public")
    public Result<List<Form>> getAllPublicForms() {
        return Result.success("Fetched all public forms", formRepository.findAll());
    }

    /**
     * Retrieve current remaining quota from Redis cache.
     */
    @GetMapping("/{formId}/quota")
    public Result<String> getQuota(@PathVariable String formId) {
        String quota = redisTemplate.opsForValue().get("form:quota:" + formId);
        return Result.success("Quota fetched", quota != null ? quota : "0");
    }

    /**
     * Reset form status (Admin Only).
     * Updates the quota and clears the submission list in Redis.
     */
    @PostMapping("/{formId}/reset/{quota}")
    public Result<String> resetQuota(@PathVariable String formId, @PathVariable int quota) {
        // Update Redis quota
        redisTemplate.opsForValue().set("form:quota:" + formId, String.valueOf(quota));

        // Clear the duplicate-check set for this form
        redisTemplate.delete("form:submitted:" + formId);

        return Result.success("Reset Successful", "New Quota: " + quota);
    }
}