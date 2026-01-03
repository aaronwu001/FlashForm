package com.flashform.core.dto;

import java.util.Map;

public class SubmissionRequest {
    private String formId;
    private String userId;

    // Receive any form answer JSON from frontend with map
    // e.g. {"email": "aaron@rutgers.edu", "age": 20}
    private Map<String, Object> answers;

    // Getter & Setter
    public String getFormId() { return formId; }
    public void setFormId(String formId) { this.formId = formId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Map<String, Object> getAnswers() { return answers; }
    public void setAnswers(Map<String, Object> answers) { this.answers = answers; }
}