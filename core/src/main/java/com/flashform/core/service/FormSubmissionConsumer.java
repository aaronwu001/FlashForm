package com.flashform.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashform.core.config.RabbitMQConfig;
import com.flashform.core.dto.SubmissionRequest;
import com.flashform.core.entity.Submission;
import com.flashform.core.repository.SubmissionRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FormSubmissionConsumer {

    @Autowired
    private SubmissionRepository submissionRepository;

    // transform Map to String
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 🎧 Listening to Queue, method triggered when message comes in.
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void receiveMessage(SubmissionRequest request) {
        System.out.println("📥 [RabbitMQ] Submission Received: " + request.getUserId());

        try {
            // 1. data transformation (DTO -> Entity) (Map -> JSON String)
            String jsonAnswers = objectMapper.writeValueAsString(request.getAnswers());

            Submission submission = new Submission(
                    request.getFormId(),
                    request.getUserId(),
                    jsonAnswers
            );

            // 2. 🔥 write into PostgreSQL
            submissionRepository.save(submission);

            System.out.println("✅ [DB] Write in successful! ID: " + submission.getId());
        } catch (Exception e) {
            System.err.println("❌ [DB] Write in failed: " + e.getMessage());
            // TODO: design Dead Letter Queue to process failure message
        }

        System.out.println("✅ [DB] Write complete!");
        System.out.println("========================================");
    }
}