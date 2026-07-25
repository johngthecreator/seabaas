package com.snapbase.dtos;

import java.util.List;

public record CreateCollectionDTO(String name, List<FieldDefinition> fields, String readRule, String updateRule) {
}
