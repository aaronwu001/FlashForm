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
            String formId = request.getFormId();
            String userId = request.getUserId();

            // Step 1: Redis Meta Check
            Map<Object, Object> meta = redisTemplate.opsForHash().entries("form:meta:" + formId);

            if (meta.isEmpty()) {
                Form form = formRepository.findById(Long.parseLong(formId)).orElse(null);
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
            redisTemplate.opsForSet().add("form:submitted:" + formId, userId);
            rabbitTemplate.convertAndSend(RabbitMQConfig.QUEUE_NAME, request);

            return 1L;

        } catch (BusinessException e) {
            // ✨ 重要：如果是我們定義的業務異常，直接拋出，不要包裝！
            // 這樣 GlobalExceptionHandler 才能抓到 400
            throw e;

        } catch (Exception e) {
            // 🔥 將所有 Checked Exception 封裝成 RuntimeException
            // 這樣 Maven 編譯就不會報錯，且 GlobalExceptionHandler 依然能抓到
            throw new RuntimeException(e.getMessage());
        }
    }

    private void checkTimeAndSchema(long start, long end, String schemaJson, Map<String, Object> answers) {
        long now = System.currentTimeMillis();
        // 使用自定義異常，確保觸發 400
        if (now < start) throw new BusinessException("Form not started yet.");
        if (now > end) throw new BusinessException("Form ended.");

        if (schemaJson != null && !schemaJson.isEmpty()) {
            try {
                List<FieldDefinition> schema = objectMapper.readValue(schemaJson, new TypeReference<>() {});
                formValidator.validate(schema, answers);
            } catch (Exception e) {
                // ✨ 這裡也改用 BusinessException
                throw new BusinessException("Schema validation error: " + e.getMessage());
            }
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