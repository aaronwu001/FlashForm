package com.flashform.core.service;

import com.flashform.core.model.FieldDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class FormValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    public void validate(List<FieldDefinition> schema, Map<String, Object> answers) {
        if (schema == null || schema.isEmpty()) {
            return;
        }

        for (FieldDefinition field : schema) {
            String key = field.getName();
            Object value = answers.get(key);

            // 1. 必填檢查
            if (field.isRequired()) {
                if (value == null || value.toString().trim().isEmpty()) {
                    throw new IllegalArgumentException("Field is required: " + key);
                }
            }

            if (value == null || value.toString().trim().isEmpty()) {
                continue;
            }

            // 2. 類型檢查
            validateType(field, value);
        }
    }

    private void validateType(FieldDefinition field, Object value) {
        String strVal = value.toString();
        switch (field.getType()) {
            case NUMBER:
                if (!isNumeric(strVal)) {
                    throw new IllegalArgumentException("Field [" + field.getName() + "] must be a number.");
                }
                break;
            case EMAIL:
                if (!EMAIL_PATTERN.matcher(strVal).matches()) {
                    throw new IllegalArgumentException("Field [" + field.getName() + "] must be a valid email.");
                }
                break;
            default:
                break;
        }
    }

    private boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}