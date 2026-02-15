package com.flashform.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashform.core.config.RabbitMQConfig;
import com.flashform.core.dto.SubmissionRequest;
import com.flashform.core.entity.Form;
import com.flashform.core.exception.BusinessException;
import com.flashform.core.model.FieldDefinition;
import com.flashform.core.repository.FormRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillService {

    private static final Logger logger = LoggerFactory.getLogger(SeckillService.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private FormRepository formRepository;

    @Autowired
    private FormValidator formValidator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Lua script instance for atomic operations
    private DefaultRedisScript<Long> seckillScript;

    /**
     * Initialize the Lua script on startup to avoid I/O overhead per request.
     */
    @PostConstruct
    public void init() {
        seckillScript = new DefaultRedisScript<>();
        seckillScript.setResultType(Long.class);
        seckillScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/seckill.lua")));
    }

    /**
     * Core Entry Point for Flash Sale (Seckill) Submissions.
     */
    public Long executeSubmission(SubmissionRequest request) {
        Long formId = request.getFormId();
        String userId = request.getUserId();

        // --- Phase 1: Java Pre-check & Cache Warm-up (Read Path) ---
        // Filters invalid requests and ensures Redis data exists.
        // If Redis is empty, it triggers the Distributed Lock mechanism to prevent DB Breakdown.
        Map<Object, Object> meta = getFormMetaWithLock(formId);

        if (meta == null || meta.isEmpty()) {
            return -2L; // Form not found
        }

        // Check for "Cache Penetration" placeholder
        if ("NOT_FOUND".equals(meta.get("status"))) {
            return -2L;
        }

        // Perform business validation (Time window & Schema validation)
        checkTimeAndSchema(meta, request.getAnswers());

        // --- Phase 2: Lua Atomic Execution (Write Path) ---
        // No Java locks needed here.
        // The Lua script guarantees atomicity for: [Duplicate Check -> Stock Check -> Decrement -> List Add]
        List<String> keys = Arrays.asList(
                "form:quota:" + formId,      // KEYS[1]: Stock Key
                "form:submitted:" + formId   // KEYS[2]: Submission List Key
        );

        // Execute Lua script
        // Returns: 1=Success, -1=Duplicate, 0=Sold Out
        Long result = redisTemplate.execute(seckillScript, keys, userId);

        // --- Phase 3: Post-processing (Async) ---
        if (result != null && result == 1L) {
            // Only send to MQ if Lua confirms the slot is secured.
            // This decouples traffic and ensures no overselling downstream.
            rabbitTemplate.convertAndSend(RabbitMQConfig.QUEUE_NAME, request);
            return 1L;
        }

        // Return the status code from Lua (0 or -1)
        return result != null ? result : 0L;
    }

    /**
     * Retrieves form metadata with Distributed Lock protection.
     * Prevents "Cache Breakdown" (Thundering Herd problem) when cache expires.
     */
    private Map<Object, Object> getFormMetaWithLock(Long formId) {
        String metaKey = "form:meta:" + formId;

        // 1. Fast Path: Cache Hit
        Map<Object, Object> meta = redisTemplate.opsForHash().entries(metaKey);
        if (!meta.isEmpty()) {
            return meta;
        }

        // 2. Slow Path: Cache Miss
        // Acquire a distributed lock to prevent multiple threads from hitting the DB simultaneously.
        String lockKey = "lock:meta_rebuild:" + formId;
        // TTL set to 10s to prevent deadlock if the server crashes while holding the lock
        Boolean acquiredLock = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", 10, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(acquiredLock)) {
            try {
                // Double-Check Locking (DCL):
                // Verify cache again in case another thread populated it while we were waiting.
                meta = redisTemplate.opsForHash().entries(metaKey);
                if (!meta.isEmpty()) {
                    return meta;
                }

                // Query Database
                logger.info("Cache miss for form {}, rebuilding from DB...", formId);
                Form form = formRepository.findById(formId).orElse(null);

                Map<String, String> newMeta = new HashMap<>();
                if (form != null) {
                    // Rebuild Meta Data
                    long startMillis = form.getStartTime().toInstant(ZoneOffset.UTC).toEpochMilli();
                    long endMillis = form.getEndTime().toInstant(ZoneOffset.UTC).toEpochMilli();

                    newMeta.put("startTime", String.valueOf(startMillis));
                    newMeta.put("endTime", String.valueOf(endMillis));
                    newMeta.put("schema", form.getSchemaJson() != null ? form.getSchemaJson() : "");
                    newMeta.put("status", "ACTIVE");

                    // Critical: Ensure the Quota key exists for the Lua script
                    redisTemplate.opsForValue().setIfAbsent("form:quota:" + formId, form.getQuota().toString());

                    // Write to Redis (TTL 24h)
                    redisTemplate.opsForHash().putAll(metaKey, newMeta);
                    redisTemplate.expire(metaKey, 24, TimeUnit.HOURS);
                } else {
                    // Prevent Cache Penetration: Cache a placeholder for non-existent IDs
                    newMeta.put("status", "NOT_FOUND");
                    redisTemplate.opsForHash().putAll(metaKey, newMeta);
                    redisTemplate.expire(metaKey, 5, TimeUnit.MINUTES); // Short TTL
                }

                return new HashMap<>(newMeta);

            } finally {
                // Always release the lock
                redisTemplate.delete(lockKey);
            }
        } else {
            // 3. Follower: Lock busy
            // Sleep briefly and retry (Spin Lock pattern)
            try {
                Thread.sleep(100); // 100ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // Recursive call to retry fetching
            return getFormMetaWithLock(formId);
        }
    }

    /**
     * In-memory validation for Time Window and JSON Schema.
     */
    private void checkTimeAndSchema(Map<Object, Object> meta, Map<String, Object> answers) {
        long now = System.currentTimeMillis();
        long start = Long.parseLong((String) meta.get("startTime"));
        long end = Long.parseLong((String) meta.get("endTime"));

        if (now < start) throw new BusinessException("Form not started yet.");
        if (now > end) throw new BusinessException("Form ended.");

        String schemaJson = (String) meta.get("schema");
        if (schemaJson != null && !schemaJson.isEmpty()) {
            try {
                List<FieldDefinition> schema = objectMapper.readValue(schemaJson, new TypeReference<>() {});
                formValidator.validate(schema, answers);
            } catch (Exception e) {
                throw new BusinessException("Schema validation error: " + e.getMessage());
            }
        }
    }
}