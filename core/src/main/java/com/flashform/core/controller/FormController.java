package com.flashform.core.controller;

import com.flashform.core.dto.FormRequest;
import com.flashform.core.entity.Form;
import com.flashform.core.repository.FormRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset; // 👈 使用標準 UTC Offset
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/forms")
public class FormController {

    @Autowired
    private FormRepository formRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

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

        // 🔥 Cache Warm-up (UTC Standard) 🔥
        redisTemplate.opsForValue().set("form:quota:" + formId, form.getQuota().toString());

        // 將 LocalDateTime 視為 UTC 時間，轉換為絕對時間戳
        long startMillis = form.getStartTime().toInstant(ZoneOffset.UTC).toEpochMilli();
        long endMillis = form.getEndTime().toInstant(ZoneOffset.UTC).toEpochMilli();

        Map<String, String> metaData = new HashMap<>();
        metaData.put("startTime", String.valueOf(startMillis));
        metaData.put("endTime", String.valueOf(endMillis));
        metaData.put("schema", form.getSchemaJson());

        redisTemplate.opsForHash().putAll("form:meta:" + formId, metaData);

        return "✅ Form created successfully! ID: " + formId;
    }
}