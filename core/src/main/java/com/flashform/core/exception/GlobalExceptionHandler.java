package com.flashform.core.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Arrays;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle JSON parsing errors (e.g., sending "abc" to an Integer field or invalid Enum values).
     * Returns 400 Bad Request with details about the invalid value and allowed options (if it's an Enum).
     */
    @ExceptionHandler(InvalidFormatException.class)
    public ResponseEntity<String> handleJsonError(InvalidFormatException e) {
        String validValues = "";

        // Check if the target type is an Enum
        // If so, provide a list of allowed values to help the frontend developer.
        if (e.getTargetType() != null && e.getTargetType().isEnum()) {
            validValues = " Allowed values are: " + Arrays.toString(e.getTargetType().getEnumConstants());
        }

        // Construct a clean error message
        // e.getValue() returns the actual invalid data sent by the user
        String errorMessage = String.format(
                "❌ JSON Parsing Error: Invalid value '%s'.%s",
                e.getValue(),
                validValues
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorMessage);
    }

    /**
     * Handle custom business logic exceptions (400 Bad Request).
     * Captures expected errors like "Quota Full", "Event Not Started", or "Invalid Input".
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<String> handleBusinessError(BusinessException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("🚫 Invalid Request: " + e.getMessage());
    }

    /**
     * Global fallback for unhandled internal exceptions (500 Internal Server Error).
     * Catches NullPointerException, SQL errors, etc.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneralError(Exception e) {
        // Log the full stack trace for server-side debugging
        e.printStackTrace();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("🔥 System Error: " + e.getMessage());
    }
}