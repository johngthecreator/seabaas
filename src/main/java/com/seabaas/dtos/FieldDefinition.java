package com.seabaas.dtos;

import com.seabaas.enums.DataTypeEnum;

public record FieldDefinition(String name, DataTypeEnum type, Boolean required) {
}
