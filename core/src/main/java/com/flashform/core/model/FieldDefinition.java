package com.flashform.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FieldDefinition implements Serializable {
    private String name;
    private FieldType type;
    private boolean required;
}