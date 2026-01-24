package com.flashform.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "submissions")
@Data   // auto-generation for Getter/Setter
@NoArgsConstructor  // auto-generation for no-argument construction
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✨ 修改點：改為 Long 型態，對應資料庫的 BIGINT
    private Long formId;

    private String userId;

    @Column(columnDefinition = "TEXT")
    private String answersJson;

    private LocalDateTime createTime;

    public Submission(Long formId, String userId, String answersJson) {
        this.formId = formId;
        this.userId = userId;
        this.answersJson = answersJson;
        this.createTime = LocalDateTime.now();
    }
}