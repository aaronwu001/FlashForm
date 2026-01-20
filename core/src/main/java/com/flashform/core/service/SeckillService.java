package com.flashform.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashform.core.config.RabbitMQConfig;
import com.flashform.core.dto.SubmissionRequest;
import com.flashform.core.entity.Form;
import com.flashform.core.model.FieldDefinition;
import com.flashform.core.repository.FormRepository;
import com.flashform.core.repository.SubmissionRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset; // 👈 Import UTC
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private FormRepository formRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private FormValidator formValidator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Long executeSubmission(SubmissionRequest request) {
        String formId = request.getFormId();
        String userId = request.getUserId();

        try {
            // Step 1: Redis Meta Check
            Map<Object, Object> meta = redisTemplate.opsForHash().entries("form:meta:" + formId);

            if (meta.isEmpty()) {
                System.out.println("⚠️ [Cache Miss] Meta not found in Redis, fetching from DB...");

                Form form = formRepository.findById(Long.parseLong(formId)).orElse(null);
                if (form == null) return -2L;

                // 🔥 UTC Conversion: 將 DB 的時間視為 UTC
                long startMillis = form.getStartTime().toInstant(ZoneOffset.UTC).toEpochMilli();
                long endMillis = form.getEndTime().toInstant(ZoneOffset.UTC).toEpochMilli();

                Map<String, String> newMeta = new HashMap<>();
                newMeta.put("startTime", String.valueOf(startMillis));
                newMeta.put("endTime", String.valueOf(endMillis));
                newMeta.put("schema", form.getSchemaJson() != null ? form.getSchemaJson() : "");

                redisTemplate.opsForHash().putAll("form:meta:" + formId, newMeta);
                redisTemplate.expire("form:meta:" + formId, 24, TimeUnit.HOURS);

                redisTemplate.opsForValue().setIfAbsent("form:quota:" + formId, form.getQuota().toString());

                System.out.println("🔧 [Cache Rebuild] Meta & Quota restored to Redis.");

                checkTimeAndSchema(startMillis, endMillis, form.getSchemaJson(), request.getAnswers());

            } else {
                // Partial Cache Miss Check (Quota Missing)
                String quotaKey = "form:quota:" + formId;
                if (Boolean.FALSE.equals(redisTemplate.hasKey(quotaKey))) {
                    System.out.println("⚠️ [Partial Cache Miss] Meta exists but Quota missing! Restoring...");
                    Form form = formRepository.findById(Long.parseLong(formId)).orElse(null);
                    if (form != null) {
                        redisTemplate.opsForValue().set(quotaKey, form.getQuota().toString());
                    } else {
                        return -2L;
                    }
                }

                long start = Long.parseLong((String) meta.get("startTime"));
                long end = Long.parseLong((String) meta.get("endTime"));
                String schemaJson = (String) meta.get("schema");

                checkTimeAndSchema(start, end, schemaJson, request.getAnswers());
            }

        } catch (IllegalArgumentException e) {
            System.err.println("❌ Validation Failed: " + e.getMessage());
            return -3L;
        } catch (Exception e) {
            e.printStackTrace();
            return -4L;
        }

        // Step 2: Idempotency Check
        if (hasUserSubmitted(formId, userId)) {
            return -1L;
        }

        // Step 3: Decrement Quota
        Long remainingQuota = redisTemplate.opsForValue().decrement("form:quota:" + formId);

        if (remainingQuota == null) return 0L;

        if (remainingQuota < 0) {
            redisTemplate.opsForValue().increment("form:quota:" + formId);
            return 0L;
        }

        // Step 4: Lock & Send
        redisTemplate.opsForSet().add("form:submitted:" + formId, userId);

        System.out.println("✅ [Service] Logic passed, sending to RabbitMQ... User: " + userId);
        rabbitTemplate.convertAndSend(RabbitMQConfig.QUEUE_NAME, request);

        return 1L;
    }

    private void checkTimeAndSchema(long start, long end, String schemaJson, Map<String, Object> answers) throws Exception {
        long now = System.currentTimeMillis();
        if (now < start) throw new IllegalArgumentException("Form not started yet.");
        if (now > end) throw new IllegalArgumentException("Form ended.");

        if (schemaJson != null && !schemaJson.isEmpty()) {
            List<FieldDefinition> schema = objectMapper.readValue(schemaJson, new TypeReference<>() {});
            formValidator.validate(schema, answers);
        }
    }

    private boolean hasUserSubmitted(String formId, String userId) {
        String cacheKey = "form:submitted:" + formId;
        String lockKey = "lock:rebuild:" + formId;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
            return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(cacheKey, userId));
        }

        Boolean acquiredLock = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", 5, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(acquiredLock)) {
            try {
                if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
                    return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(cacheKey, userId));
                }

                System.out.println("🔧 [Cache Rebuild] Rebuilding submitted set for form: " + formId);
                List<String> userIds = submissionRepository.findAllUserIdsByFormId(formId);

                if (!userIds.isEmpty()) {
                    redisTemplate.opsForSet().add(cacheKey, userIds.toArray(new String[0]));
                    redisTemplate.expire(cacheKey, 24, TimeUnit.HOURS);
                } else {
                    redisTemplate.opsForSet().add(cacheKey, "EMPTY_PLACEHOLDER");
                    redisTemplate.expire(cacheKey, 5, TimeUnit.MINUTES);
                }
                return userIds.contains(userId);

            } finally {
                redisTemplate.delete(lockKey);
            }
        } else {
            try { Thread.sleep(50); } catch (InterruptedException e) {}
            return hasUserSubmitted(formId, userId);
        }
    }
}