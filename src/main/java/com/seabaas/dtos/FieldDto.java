package com.seabaas.dtos;

import com.seabaas.enums.DataTypeEnum;

public record FieldDto(String name, DataTypeEnum type, Boolean required) {
}
