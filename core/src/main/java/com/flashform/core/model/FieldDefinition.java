package com.flashform.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FieldDefinition implements Serializable {
    private String name;      // 欄位名稱 (JSON Key)，例如 "age"
    private FieldType type;   // 類型，例如 NUMBER
    private boolean required; // 是否必填
}