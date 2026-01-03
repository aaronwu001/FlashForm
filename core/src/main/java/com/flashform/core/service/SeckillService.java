package com.flashform.core.service;

import com.flashform.core.dto.SubmissionRequest;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class SeckillService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private DefaultRedisScript<Long> seckillScript;

    @PostConstruct
    public void init() {
        seckillScript = new DefaultRedisScript<>();
        seckillScript.setLocation(new ClassPathResource("scripts/seckill.lua"));
        seckillScript.setResultType(Long.class);
    }

    /**
     * execute form submission (atomic check)
     * put parameters in DTO, for future expansion.
     */
    public Long executeSubmission(SubmissionRequest request) {
        String formId = request.getFormId();
        String userId = request.getUserId();

        // form:quota:101 (remaining quota)
        String quotaKey = "form:quota:" + formId;
        // form:submitted:101 (submitted user set)
        String submittedKey = "form:submitted:" + formId;

        List<String> keys = Arrays.asList(quotaKey, submittedKey);

        // Execute Lua script
        return redisTemplate.execute(seckillScript, keys, userId);
    }
}