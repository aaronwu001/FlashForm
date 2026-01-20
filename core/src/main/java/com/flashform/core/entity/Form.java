package com.flashform.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "forms")
@Data
@NoArgsConstructor
public class Form {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 發起表單的人 (可以是管理員 ID 或 User ID)
    private String ownerId;

    private String title;

    // 關鍵欄位：存儲表單結構定義 (JSON String)
    // 例如: [{"name":"age", "type":"NUMBER", "required":true}]
    @Column(columnDefinition = "TEXT")
    private String schemaJson;

    // 總量限制 (庫存)
    private Integer quota;

    // 表單開啟與關閉時間
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // 方便創建的建構子
    public Form(String ownerId, String title, String schemaJson, Integer quota, LocalDateTime startTime, LocalDateTime endTime) {
        this.ownerId = ownerId;
        this.title = title;
        this.schemaJson = schemaJson;
        this.quota = quota;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}