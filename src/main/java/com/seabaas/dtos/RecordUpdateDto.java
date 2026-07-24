package com.seabaas.dtos;

import java.util.Map;

public record RecordUpdateDto(String name, Map<String, Object> data, Map<String, String> filter) {
}
