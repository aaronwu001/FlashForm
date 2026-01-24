package com.flashform.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashform.core.config.RabbitMQConfig;
import com.flashform.core.dto.SubmissionRequest;
import com.flashform.core.entity.Form;
import com.flashform.core.exception.BusinessException;
import com.flashform.core.model.FieldDefinition;
import com.flashform.core.repository.FormRepository;
import com.flashform.core.repository.SubmissionRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
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
        try {
            // ✨ 修改點 1: 這裡現在直接獲取 Long
            Long formId = request.getFormId();
            String userId = request.getUserId();

            // Step 1: Redis Meta Check
            // 注意：Java 會自動將 Long 轉為 String 進行拼接，所以 "form:meta:" + formId 依然有效
            Map<Object, Object> meta = redisTemplate.opsForHash().entries("form:meta:" + formId);

            if (meta.isEmpty()) {
                // ✨ 修改點 2: 直接傳入 Long，不需要 Long.parseLong()
                Form form = formRepository.findById(formId).orElse(null);
                if (form == null) return -2L;

                long startMillis = form.getStartTime().toInstant(ZoneOffset.UTC).toEpochMilli();
                long endMillis = form.getEndTime().toInstant(ZoneOffset.UTC).toEpochMilli();

                Map<String, String> newMeta = new HashMap<>();
                newMeta.put("startTime", String.valueOf(startMillis));
                newMeta.put("endTime", String.valueOf(endMillis));
                newMeta.put("schema", form.getSchemaJson() != null ? form.getSchemaJson() : "");

                redisTemplate.opsForHash().putAll("form:meta:" + formId, newMeta);
                redisTemplate.expire("form:meta:" + formId, 24, TimeUnit.HOURS);
                redisTemplate.opsForValue().setIfAbsent("form:quota:" + formId, form.getQuota().toString());

                checkTimeAndSchema(startMillis, endMillis, form.getSchemaJson(), request.getAnswers());
            } else {
                String quotaKey = "form:quota:" + formId;
                if (Boolean.FALSE.equals(redisTemplate.hasKey(quotaKey))) {
                    // ✨ 修改點 3: 同樣直接傳入 Long
                    Form form = formRepository.findById(formId).orElse(null);
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

            // Step 2: Idempotency Check
            if (hasUserSubmitted(formId, userId)) {
                return -1L;
            }

            // Step 3: Decrement Quota
            Long remainingQuota = redisTemplate.opsForValue().decrement("form:quota:" + formId);
            if (remainingQuota == null || remainingQuota < 0) {
                if (remainingQuota != null) redisTemplate.opsForValue().increment("form:quota:" + formId);
                return 0L;
            }

            // Step 4: Lock & Send
            String submittedKey = "form:submitted:" + formId;
            redisTemplate.opsForSet().add(submittedKey, userId);

            // ✨ 重要修改：補上過期時間，防止被之前的「5分鐘佔位符」影響導致名單消失
            redisTemplate.expire(submittedKey, 24, TimeUnit.HOURS);

            rabbitTemplate.convertAndSend(RabbitMQConfig.QUEUE_NAME, request);

            return 1L;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private void checkTimeAndSchema(long start, long end, String schemaJson, Map<String, Object> answers) {
        long now = System.currentTimeMillis();
        if (now < start) throw new BusinessException("Form not started yet.");
        if (now > end) throw new BusinessException("Form ended.");

        if (schemaJson != null && !schemaJson.isEmpty()) {
            try {
                List<FieldDefinition> schema = objectMapper.readValue(schemaJson, new TypeReference<>() {});
                formValidator.validate(schema, answers);
            } catch (Exception e) {
                throw new BusinessException("Schema validation error: " + e.getMessage());
            }
        }
    }

    // ✨ 修改點 4: 參數改為 Long formId
    private boolean hasUserSubmitted(Long formId, String userId) {
        String cacheKey = "form:submitted:" + formId;
        String lockKey = "lock:rebuild:" + formId;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
            return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(cacheKey, userId));
        }

        Boolean acquiredLock = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", 5, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(acquiredLock)) {
            try {
                // ✨ 修改點 5: Repository 已經改成接收 Long 了，這裡直接傳入
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
            try { Thread.sleep(50); } catch (Exception e) {}
            return hasUserSubmitted(formId, userId);
        }
    }
}