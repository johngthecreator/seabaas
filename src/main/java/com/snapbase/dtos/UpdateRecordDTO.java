package com.snapbase.dtos;

import java.util.Map;

public record UpdateRecordDTO(String name, Map<String, Object> data, Map<String, String> filter) {
}
