package com.flashform.core.exception;

/**
 * 代表業務邏輯錯誤（如時間不對、格式不符、配額已滿）
 * 這些錯誤通常歸類為客戶端錯誤 (400)
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}