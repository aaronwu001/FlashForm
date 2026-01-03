package com.flashform.core.service;

import com.flashform.core.config.RabbitMQConfig;
import com.flashform.core.dto.SubmissionRequest;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class FormSubmissionConsumer {

    // 🎧 Listening to Queue, method triggered when message comes in.
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void receiveMessage(SubmissionRequest request) {
        System.out.println("========================================");
        System.out.println("📥 [RabbitMQ Consumer] New form received!");
        System.out.println("   User: " + request.getUserId());
        System.out.println("   Form: " + request.getFormId());
        System.out.println("   Answers: " + request.getAnswers());
        System.out.println("🛠️  [DB] Writing into database... (Simulation)");
        try {
            Thread.sleep(1000); // simulate writing to DB for 1 sec.
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("✅ [DB] Write complete!");
        System.out.println("========================================");
    }
}