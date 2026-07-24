package com.seabaas.services;

import com.seabaas.auth.Role;
import com.seabaas.dtos.CollectionDto;
import com.seabaas.dtos.CollectionInsertDto;
import com.seabaas.dtos.RecordUpdateDto;
import com.seabaas.models.CollectionModel;
import com.seabaas.repositories.CollectionRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class CollectionService {
    private final CollectionRepository collectionRepository;

    public CollectionService(CollectionRepository collectionRepository){
        this.collectionRepository = collectionRepository;
    }

    public Integer create(CollectionDto collection){
        var exists = this.collectionRepository.findByName(collection.name());
        if(exists != 0){ return -100; }
        this.collectionRepository.createTable(collection);
        return this.collectionRepository.save(collection);
    };

    public String insertRecord(CollectionInsertDto collectionInsertData, String userId){
        return this.collectionRepository.insertRecord(collectionInsertData, userId);
    }

    public void updateRecord(RecordUpdateDto recordUpdateData, String userId, Set<Role> roles){
        var col = this.collectionRepository.findSchemaByName(recordUpdateData.name());
        String updateRule = col.map(CollectionModel::getUpdate_rule).orElse("ALL");
        boolean isAdmin = roles.contains(Role.ADMIN);
        this.collectionRepository.updateRecord(recordUpdateData, updateRule, userId, isAdmin);
    }

    public Integer deleteRecord(String collectionName, Map<String, List<String>> queryParams, String userId, Set<Role> roles){
        var col = this.collectionRepository.findSchemaByName(collectionName);
        String updateRule = col.map(CollectionModel::getUpdate_rule).orElse("ALL");
        boolean isAdmin = roles.contains(Role.ADMIN);
        return this.collectionRepository.deleteRecord(collectionName, queryParams, updateRule, userId, isAdmin);
    }

    public List<Map<String, Object>> findRecords(String collectionName, Map<String, List<String>> queryParams, String userId, Set<Role> roles){
        var col = this.collectionRepository.findSchemaByName(collectionName);
        String readRule = col.map(CollectionModel::getRead_rule).orElse("ALL");
        boolean isAdmin = roles.contains(Role.ADMIN);
        return this.collectionRepository.findRecords(collectionName, queryParams, readRule, userId, isAdmin);
    }

    public List<CollectionModel> listAll() {
        return this.collectionRepository.findAll();
    }

    public Optional<CollectionModel> getSchema(String name) {
        return this.collectionRepository.findSchemaByName(name);
    }

}
