package com.flashform.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashform.core.config.RabbitMQConfig;
import com.flashform.core.dto.SubmissionRequest;
import com.flashform.core.entity.Submission;
import com.flashform.core.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class FormSubmissionConsumer {

    private static final Logger logger = LoggerFactory.getLogger(FormSubmissionConsumer.class);

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Listens to the submission queue and persists data to the database.
     * concurrency = "5-10": Starts with 5 consumers, scaling up to 10 under load.
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME, concurrency = "5-10")
    public void receiveMessage(SubmissionRequest request) {
        logger.info("📥 [RabbitMQ] Processing submission for User: {}", request.getUserId());

        try {
            // Convert answers map to JSON string
            String jsonAnswers = objectMapper.writeValueAsString(request.getAnswers());

            Submission submission = new Submission(
                    request.getFormId(),
                    request.getUserId(),
                    jsonAnswers,
                    request.getClientTimestamp() // 傳入 DTO 的值
            );

            submissionRepository.save(submission);

            logger.info("✅ [DB] Saved successfully! ID: {}", submission.getId());

        } catch (DataIntegrityViolationException e) {
            // Idempotency check: Ignore duplicates caused by MQ retries or race conditions.
            // We swallow this exception to acknowledge the message and remove it from the queue.
            logger.warn("⚠️ [DB] Duplicate submission detected for User: {}. Ignoring.", request.getUserId());

        } catch (Exception e) {
            // Log the error and re-throw to trigger NACK/Retry policy in RabbitMQ.
            // This ensures data is not lost in case of transient DB failures.
            logger.error("❌ [DB] Write failed: {}", e.getMessage());
            throw new RuntimeException("DB Write Failed, retrying...", e);
        }
    }
}