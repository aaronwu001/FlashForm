package com.flashform.core.model;
import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public enum FieldType {
    TEXT,
    NUMBER,
    EMAIL,

    @JsonEnumDefaultValue
    UNKNOWN
}