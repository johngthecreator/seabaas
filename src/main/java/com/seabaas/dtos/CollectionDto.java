package com.seabaas.dtos;

import java.util.List;

public record CollectionDto(String name, List<FieldDto> fields, String readRule, String updateRule) {
}
