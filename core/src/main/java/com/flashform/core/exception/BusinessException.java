package com.flashform.core.exception;

/**
 * Represents business logic errors (e.g., invalid timing, format issues, or exhausted quota).
 * These exceptions are typically categorized as client-side errors (400 Bad Request).
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}