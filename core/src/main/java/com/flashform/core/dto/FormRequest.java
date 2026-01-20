package com.flashform.core.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FormRequest {
    // 這裡不需要 id，因為是創建新表單
    private String ownerId;
    private String title;
    private String schemaJson;
    private Integer quota;

    // 注意：前端傳入的時間格式預設是 ISO-8601 (e.g., "2024-01-20T10:00:00")
    // Spring Boot 會自動幫我們轉成 LocalDateTime
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}