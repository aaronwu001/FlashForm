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

    // The creator of the form (can be an Admin ID or User ID)
    private String ownerId;

    private String title;

    /**
     * Stores form structure definition as a JSON string.
     * e.g., [{"name":"age", "type":"NUMBER", "required":true}]
     */
    @Column(columnDefinition = "TEXT")
    private String schemaJson;

    // Total submission limit (Inventory/Quota)
    private Integer quota;

    // Form availability window
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // Constructor for manual object creation
    public Form(String ownerId, String title, String schemaJson, Integer quota, LocalDateTime startTime, LocalDateTime endTime) {
        this.ownerId = ownerId;
        this.title = title;
        this.schemaJson = schemaJson;
        this.quota = quota;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}