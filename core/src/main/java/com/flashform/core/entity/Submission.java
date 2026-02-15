package com.flashform.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(
        name = "submissions",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"formId", "userId"})
        }
)
@Data
@NoArgsConstructor
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long formId;

    private String userId;

    @Column(columnDefinition = "TEXT")
    private String answersJson;

    // Time received by Server
    private LocalDateTime createTime;

    // Time submitted by User
    private LocalDateTime clientTime;

    public Submission(Long formId, String userId, String answersJson, Long clientTimestamp) {
        this.formId = formId;
        this.userId = userId;
        this.answersJson = answersJson;

        // 1. set server time (required)
        this.createTime = LocalDateTime.now();

        // 2. set client time (optional, must prevent NullPointerException)
        if (clientTimestamp != null) {
            this.clientTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(clientTimestamp),
                    ZoneId.of("UTC") // Assume UTM Timestamp from frontend
            );
        }
    }
}