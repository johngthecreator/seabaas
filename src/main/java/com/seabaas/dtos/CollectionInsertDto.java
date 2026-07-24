package com.seabaas.dtos;

import java.util.Map;

public record CollectionInsertDto(String name, Map<String, Object> data) {}
