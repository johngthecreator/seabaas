package com.snapbase.dtos;

import com.snapbase.enums.DataTypeEnum;

public record FieldDefinition(String name, DataTypeEnum type, Boolean required) {
}
