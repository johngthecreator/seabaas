package com.snapbase.services;

import com.snapbase.auth.Role;
import com.snapbase.dtos.CreateCollectionDTO;
import com.snapbase.dtos.InsertRecordDTO;
import com.snapbase.dtos.UpdateRecordDTO;
import com.snapbase.models.CollectionModel;
import com.snapbase.repositories.CollectionRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class CollectionService {
    private final CollectionRepository collectionRepository;

    public CollectionService(CollectionRepository collectionRepository) {
        this.collectionRepository = collectionRepository;
    }

    public Integer create(CreateCollectionDTO collection) {
        if (collectionRepository.collectionExists(collection.name())) {
            return -100;
        }
        collectionRepository.createTable(collection);
        return collectionRepository.saveCollection(collection);
    }

    public Integer update(CreateCollectionDTO collection) {
        if (!collectionRepository.collectionExists(collection.name())) {
            return -100;
        }
        collectionRepository.updateTable(collection);
        collectionRepository.updateCollectionMeta(collection);
        return 1;
    }

    public void delete(String name) {
        if ("users".equals(name) || "superusers".equals(name)) {
            throw new com.snapbase.exceptions.ResponseException(403, "Cannot delete system collections");
        }
        if (!collectionRepository.collectionExists(name)) {
            throw new com.snapbase.exceptions.ResponseException(404, "Collection not found");
        }
        collectionRepository.dropTable(name);
        collectionRepository.deleteMeta(name);
    }

    public String insertRecord(InsertRecordDTO dto, String userId) {
        return collectionRepository.insertRecord(dto, userId);
    }

    public int updateRecords(UpdateRecordDTO dto, String userId, Set<Role> roles) {
        var col = collectionRepository.findCollectionSchema(dto.name());
        String updateRule = col.map(CollectionModel::getUpdate_rule).orElse("ALL");
        boolean isAdmin = roles.contains(Role.ADMIN);
        return collectionRepository.updateRecords(dto, updateRule, userId, isAdmin);
    }

    public int deleteRecords(String collectionName, Map<String, List<String>> queryParams, String userId, Set<Role> roles) {
        var col = collectionRepository.findCollectionSchema(collectionName);
        String updateRule = col.map(CollectionModel::getUpdate_rule).orElse("ALL");
        boolean isAdmin = roles.contains(Role.ADMIN);
        return collectionRepository.deleteRecords(collectionName, queryParams, updateRule, userId, isAdmin);
    }

    public List<Map<String, Object>> findRecords(String collectionName, Map<String, List<String>> queryParams, String userId, Set<Role> roles) {
        var col = collectionRepository.findCollectionSchema(collectionName);
        String readRule = col.map(CollectionModel::getRead_rule).orElse("ALL");
        boolean isAdmin = roles.contains(Role.ADMIN);
        return collectionRepository.findRecords(collectionName, queryParams, readRule, userId, isAdmin);
    }

    public List<CollectionModel> listAll() {
        return collectionRepository.findAllCollections();
    }

    public Optional<CollectionModel> getSchema(String name) {
        return collectionRepository.findCollectionSchema(name);
    }
}
