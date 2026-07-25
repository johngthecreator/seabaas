package com.snapbase.controllers;

import com.snapbase.auth.JwtUtils;
import com.snapbase.auth.Role;
import com.snapbase.dtos.CreateCollectionDTO;
import com.snapbase.dtos.InsertRecordDTO;
import com.snapbase.dtos.UpdateRecordDTO;
import com.snapbase.exceptions.ResponseException;
import com.snapbase.services.CollectionService;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class CollectionController {
    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    private record AuthContext(String userId, Set<Role> roles) {}

    private AuthContext extractAuth(Context ctx) {
        String header = ctx.header("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return new AuthContext(null, Set.of());
        }
        String token = header.substring(7);
        return new AuthContext(JwtUtils.getUserId(token), JwtUtils.validate(token));
    }

    public void create(Context ctx) {
        try {
            var dto = ctx.bodyValidator(CreateCollectionDTO.class).get();
            var result = this.collectionService.create(dto);
            if (result == -100) {
                throw new ResponseException(409, "Collection already exists");
            }
            ctx.status(200).json(Map.of("status", "success", "data", Map.of("created_schema_id", result)));
        } catch (ResponseException e) {
            ctx.status(e.getStatus()).json(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("status", "error", "message", "Internal server error"));
        }
    }

    public void insertRecord(Context ctx) {
        try {
            var auth = extractAuth(ctx);
            var dto = ctx.bodyValidator(InsertRecordDTO.class).get();
            var result = this.collectionService.insertRecord(dto, auth.userId());
            ctx.status(200).json(Map.of("status", "success", "data", Map.of("row_created", result)));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("status", "error", "message", "Internal server error"));
        }
    }

    public void updateRecord(Context ctx) {
        try {
            var auth = extractAuth(ctx);
            var dto = ctx.bodyValidator(UpdateRecordDTO.class).get();
            int updated = this.collectionService.updateRecords(dto, auth.userId(), auth.roles());
            if (updated == 0) {
                ctx.status(404).json(Map.of("status", "error", "message", "No matching records found"));
            } else {
                ctx.status(200).json(Map.of("status", "success", "data", Map.of("updated", updated)));
            }
        } catch (Exception e) {
            ctx.status(500).json(Map.of("status", "error", "message", "Internal server error"));
        }
    }

    public void deleteRecords(Context ctx) {
        try {
            var auth = extractAuth(ctx);
            Map<String, List<String>> queryParams = ctx.queryParamMap();
            String collectionName = ctx.pathParam("collection");
            this.collectionService.deleteRecords(collectionName, queryParams, auth.userId(), auth.roles());
            ctx.status(200).json(Map.of("status", "success"));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("status", "error", "message", "Internal server error"));
        }
    }

    public void findRecords(Context ctx) {
        try {
            var auth = extractAuth(ctx);
            Map<String, List<String>> queryParams = ctx.queryParamMap();
            String collectionName = ctx.pathParam("collection");
            var results = this.collectionService.findRecords(collectionName, queryParams, auth.userId(), auth.roles());
            ctx.status(200).json(Map.of("status", "success", "data", results));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("status", "error", "message", "Internal server error"));
        }
    }

    public void listCollections(Context ctx) {
        try {
            var collections = this.collectionService.listAll();
            ctx.status(200).json(Map.of("status", "success", "data", collections));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("status", "error", "message", "Internal server error"));
        }
    }

    public void getSchema(Context ctx) {
        try {
            String name = ctx.pathParam("name");
            var schema = this.collectionService.getSchema(name);
            if (schema.isPresent()) {
                ctx.status(200).json(Map.of("status", "success", "data", schema.get()));
            } else {
                ctx.status(404).json(Map.of("status", "error", "message", "Collection not found"));
            }
        } catch (Exception e) {
            ctx.status(500).json(Map.of("status", "error", "message", "Internal server error"));
        }
    }
}
