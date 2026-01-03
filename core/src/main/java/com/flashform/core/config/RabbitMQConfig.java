package com.flashform.core.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Define Queue Name
    public static final String QUEUE_NAME = "form.submission.queue";

    @Bean
    public Queue formSubmissionQueue() {
        // true = durable (Queue persist even when RabbitMQ restart)
        return new Queue(QUEUE_NAME, true);
    }

    // 🔥 Convert Message to JSON using Jackson
    // This overwrites the default Java Serialization
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

}