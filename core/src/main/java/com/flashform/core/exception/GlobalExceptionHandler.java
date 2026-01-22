package com.flashform.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Arrays;

@RestControllerAdvice // 這個註解讓它能「監聽」所有 Controller 的異常
public class GlobalExceptionHandler {

    // 專門攔截 Jackson 解析 JSON 失敗的錯誤 (例如 Enum 對不上)
    @ExceptionHandler(com.fasterxml.jackson.databind.exc.InvalidFormatException.class)
    public ResponseEntity<String> handleJsonError(com.fasterxml.jackson.databind.exc.InvalidFormatException e) {
        // 嘗試取得允許的 Enum 值 (如果有的話)
        String validValues = "";
        if (e.getTargetType() != null && e.getTargetType().isEnum()) {
            validValues = " Allowed values are: " + Arrays.toString(e.getTargetType().getEnumConstants());
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST) // 回傳 400
                .body("❌ JSON Parsing Error: Invalid value '" + e.getValue() + "'." + validValues);
    }

    // 攔截所有其他未知的錯誤 (兜底機制)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneralError(Exception e) {
        // 在後台印出詳細錯誤，方便工程師查修
        e.printStackTrace();

        // 告訴前端發生了 Server Error，但不要洩漏太多細節
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR) // 回傳 500
                .body("🔥 System Error: " + e.getMessage());
    }
}