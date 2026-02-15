package com.flashform.core.dto;

import lombok.Data;
import java.io.Serializable;
import java.util.Map;

@Data
public class SubmissionRequest implements Serializable {

    private Long formId;

    private String userId;

    // Receive any form answer JSON from frontend with map
    // e.g. {"email": "aaron@rutgers.edu", "age": 20}
    private Map<String, Object> answers;

    private Long clientTimestamp;
}