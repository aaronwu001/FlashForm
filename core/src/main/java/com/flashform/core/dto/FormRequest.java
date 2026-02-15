package com.flashform.core.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FormRequest {
    // ID is omitted as this DTO is specifically for new form creation
    private String ownerId;
    private String title;
    private String schemaJson;
    private Integer quota;

    /**
     * Frontend timestamps are expected in ISO-8601 format (e.g., "2024-01-20T10:00:00").
     * Spring Boot automatically deserializes these into LocalDateTime.
     */
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}