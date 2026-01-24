package com.flashform.core.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class SubmissionRequest implements Serializable {

    // ✨ 修改點：將原本的 String 改為 Long，以配合資料庫與 Repository 的改動
    private Long formId;

    private String userId;

    // Receive any form answer JSON from frontend with map
    // e.g. {"email": "aaron@rutgers.edu", "age": 20}
    private Map<String, Object> answers;

    // Getter & Setter (保留原本的顯式寫法，僅修改 formId 的型別)
    public Long getFormId() { return formId; }
    public void setFormId(Long formId) { this.formId = formId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Map<String, Object> getAnswers() { return answers; }
    public void setAnswers(Map<String, Object> answers) { this.answers = answers; }
}