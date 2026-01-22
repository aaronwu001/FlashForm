package com.flashform.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Arrays;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. 你原本的 JSON 解析錯誤處理 (400)
    @ExceptionHandler(com.fasterxml.jackson.databind.exc.InvalidFormatException.class)
    public ResponseEntity<String> handleJsonError(com.fasterxml.jackson.databind.exc.InvalidFormatException e) {
        // ... 你原本的邏輯 ...
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("❌ JSON Error: " + e.getMessage());
    }

    // ✨ 2. 新增：專門處理我們定義的業務錯誤 (400)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<String> handleBusinessError(BusinessException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST) // 強制回傳 400
                .body("🚫 Invalid Request: " + e.getMessage());
    }

    // 3. 你原本的兜底機制 (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneralError(Exception e) {
        e.printStackTrace();
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("🔥 System Error: " + e.getMessage());
    }
}