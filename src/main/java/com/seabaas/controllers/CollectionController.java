package com.seabaas.controllers;

import com.seabaas.auth.JwtUtils;
import com.seabaas.auth.Role;
import com.seabaas.dtos.CollectionDto;
import com.seabaas.dtos.CollectionInsertDto;
import com.seabaas.dtos.RecordUpdateDto;
import com.seabaas.services.CollectionService;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class CollectionController {
    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService){
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

    public void create(Context ctx){
        var dto = ctx.bodyValidator(CollectionDto.class).get();
        var result = this.collectionService.create(dto);
        if(result == -100){
            ctx.status(500).json(Map.of("status","success","message","schema exists"));
        }else{
            ctx.status(200).json(Map.of("status","success","data", Map.of("created_schema_id", result)));
        }
    }

    public void insertRecord(Context ctx){
        var auth = extractAuth(ctx);
        var dto = ctx.bodyValidator(CollectionInsertDto.class).get();
        var result = this.collectionService.insertRecord(dto, auth.userId());
        ctx.status(200).json(Map.of("status","success","data", Map.of("row_created", result)));
    }

    public void updateRecord(Context ctx){
        var auth = extractAuth(ctx);
        var dto = ctx.bodyValidator(RecordUpdateDto.class).get();
        this.collectionService.updateRecord(dto, auth.userId(), auth.roles());
        ctx.status(200).json(Map.of("status","success"));
    }

    public void deleteRecords(Context ctx){
        var auth = extractAuth(ctx);
        Map<String, List<String>> queryParams = ctx.queryParamMap();
        String collectionName = ctx.pathParam("collection");
        var results = this.collectionService.deleteRecord(collectionName, queryParams, auth.userId(), auth.roles());
        System.out.println(results);
        ctx.status(200).json(Map.of("status","success", "message", "successfully deleted record."));
    }

    public void findRecords(Context ctx){
        var auth = extractAuth(ctx);
        Map<String, List<String>> queryParams = ctx.queryParamMap();
        String collectionName = ctx.pathParam("collection");
        var results = this.collectionService.findRecords(collectionName, queryParams, auth.userId(), auth.roles());
        ctx.status(200).json(Map.of("status","success","data", results));
    }

    public void listCollections(Context ctx) {
        var collections = this.collectionService.listAll();
        ctx.status(200).json(Map.of("status","success","data", collections));
    }

    public void getSchema(Context ctx) {
        String name = ctx.pathParam("name");
        var schema = this.collectionService.getSchema(name);
        if (schema.isPresent()) {
            ctx.status(200).json(Map.of("status","success","data", schema.get()));
        } else {
            ctx.status(404).json(Map.of("status","failure","message","Collection not found"));
        }
    }
}
