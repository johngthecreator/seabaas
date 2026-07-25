package com.snapbase.dtos;

import java.util.Map;

public record InsertRecordDTO(String name, Map<String, Object> data) {}
