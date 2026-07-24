package com.seabaas.repositories;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seabaas.dtos.CollectionDto;
import com.seabaas.dtos.CollectionInsertDto;
import com.seabaas.dtos.FieldDto;
import com.seabaas.dtos.RecordUpdateDto;
import com.seabaas.enums.DataTypeEnum;
import com.seabaas.models.CollectionModel;
import org.jdbi.v3.core.Jdbi;

import com.seabaas.utils.SqlUtils;

import java.util.*;
import java.util.stream.Collectors;

public class CollectionRepository {
    private final Jdbi jdbi;

    public CollectionRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public Integer findByName(String name){
        List<CollectionModel> allCollections = this.jdbi.withHandle(handle -> {
            return handle.createQuery("SELECT * FROM collections WHERE name = :name")
                    .bind("name", name)
                    .mapToBean(CollectionModel.class)
                    .list();
        });
        return allCollections.size();
    }
    public void createTable(CollectionDto collection){
        String tableName = SqlUtils.quoteIdentifier(collection.name());
        String columns = collection.fields().stream().map(this::toSqlString).collect(Collectors.joining(", "));
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " ( id TEXT PRIMARY KEY, " +
                columns + ", created_at TEXT DEFAULT CURRENT_TIMESTAMP, created_by TEXT DEFAULT '' );";
        this.jdbi.withHandle(handle ->
           handle.execute(sql)
       );
    }
    public Integer save(CollectionDto collection){
        return this.jdbi.withHandle(handle -> {
            try {
                return handle.createUpdate("INSERT INTO collections (name, json_schema, read_rule, update_rule) VALUES (:name, :jsonSchema, :readRule, :updateRule)")
                        .bind("name", collection.name())
                        .bind("jsonSchema", new ObjectMapper().writeValueAsString(collection.fields()))
                        .bind("readRule", collection.readRule())
                        .bind("updateRule", collection.updateRule())
                        .executeAndReturnGeneratedKeys("id")
                        .mapTo(Integer.TYPE)
                        .one();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public String insertRecord(CollectionInsertDto collectionInsertData, String userId){
        var allColumns = new LinkedHashSet<String>();
        allColumns.add("id");
        allColumns.add("created_by");
        allColumns.addAll(collectionInsertData.data().keySet());

        String columns = allColumns.stream()
                .map(SqlUtils::quoteIdentifier)
                .collect(Collectors.joining(", "));

        String placeholders = allColumns.stream()
                .map(k -> ":" + SqlUtils.validateIdentifier(k))
                .collect(Collectors.joining(", "));

        String table = SqlUtils.quoteIdentifier(collectionInsertData.name());
        String sql = "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")";

        String id = SqlUtils.generateId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("created_by", userId != null ? userId : "");
        data.putAll(collectionInsertData.data());

        jdbi.withHandle(handle ->
                handle.createUpdate(sql)
                        .bindMap(data)
                        .execute()
        );
        return id;
    }

    public void updateRecord(RecordUpdateDto recordUpdateData, String updateRule, String userId, boolean isAdmin){
        String sql = this.toUpdateSql(recordUpdateData.name(), recordUpdateData.data(), recordUpdateData.filter(), updateRule, userId, isAdmin);
        this.jdbi.withHandle(handle -> handle.execute(sql));
    }

    public List<CollectionModel> findAll() {
        return jdbi.withHandle(handle ->
            handle.createQuery("SELECT * FROM collections ORDER BY rowid ASC")
                  .mapToBean(CollectionModel.class)
                  .list()
        );
    }

    public Optional<CollectionModel> findSchemaByName(String name) {
        return jdbi.withHandle(handle ->
            handle.createQuery("SELECT * FROM collections WHERE name = :name")
                  .bind("name", name)
                  .mapToBean(CollectionModel.class)
                  .findOne()
        );
    }

    public Integer deleteRecord(String collectionName, Map<String, List<String>> queryParams, String updateRule, String userId, boolean isAdmin){
        String sql = this.toDeleteSql(collectionName, queryParams, updateRule, userId, isAdmin);
        System.out.println(sql);
        return this.jdbi.withHandle(handle -> handle.execute(sql));
    }

    public String toUpdateSql(String collectionName, Map<String, Object> data, Map<String, String> filter, String updateRule, String userId, boolean isAdmin){
        List<String> sqlStrings = new ArrayList<>();
        int i = 0;
        int dataI = 0;
        sqlStrings.add("UPDATE " + SqlUtils.quoteIdentifier(collectionName));
        for (var dataEntry : data.entrySet()){
            if(dataI < 1){
                sqlStrings.add(" SET " + SqlUtils.quoteIdentifier(dataEntry.getKey()) +" = " + dataEntry.getValue());
                dataI++;
            }else{
                sqlStrings.add(", " + SqlUtils.quoteIdentifier(dataEntry.getKey()) +" = " + dataEntry.getValue());
                dataI++;
            }
        }
        for (var filterEntry : filter.entrySet()){
            if(i < 1){
                sqlStrings.add(" WHERE " + SqlUtils.quoteIdentifier(filterEntry.getKey()) +" " + filterEntry.getValue());
                i++;
            }else{
                sqlStrings.add(" AND " + SqlUtils.quoteIdentifier(filterEntry.getKey()) +" " + filterEntry.getValue());
                i++;
            }
        }
        if (!isAdmin && "USER".equals(updateRule) && userId != null) {
            sqlStrings.add(i == 0 ? " WHERE " : " AND ");
            sqlStrings.add("\"created_by\" = '" + userId + "'");
        }
        sqlStrings.add(";");
        return String.join("",sqlStrings);
    }



    public List<Map<String, Object>> findRecords(String collectionName, Map<String, List<String>> queryParams, String readRule, String userId, boolean isAdmin){
        String sql = this.toQuerySql(collectionName, queryParams, readRule, userId, isAdmin);
        return this.jdbi.withHandle(handle ->   handle.createQuery(sql)
                .map((rs, ctx) -> {
                    Map<String, Object> row = new HashMap<>();
                    var meta = rs.getMetaData();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnName(i), rs.getObject(i));
                    }
                    return row;
                })
                .list()
        );
    }

    private String toDeleteSql(String collectionName, Map<String, List<String>> data, String updateRule, String userId, boolean isAdmin){
        var filterCount = 0;
        List<String> sqlStrings = new ArrayList<>();
        sqlStrings.add("DELETE FROM " + SqlUtils.quoteIdentifier(collectionName));
        for (var entry : data.entrySet()){
            var keyData = entry.getKey().split(":", 2);
            if (keyData.length < 2 || !"filter".equals(keyData[0])) continue;
            var filterName = SqlUtils.quoteIdentifier(keyData[1]);
            var filterData = entry.getValue();
            for (var i : filterData){
                if(filterCount == 0){
                    sqlStrings.add(" WHERE ");
                    sqlStrings.add(filterName + " " + i);
                    filterCount++;
                }else{
                    sqlStrings.add(" AND ");
                    sqlStrings.add(filterName + " " + i);
                    filterCount++;
                }
            }
        }
        if (!isAdmin && "USER".equals(updateRule) && userId != null) {
            sqlStrings.add(filterCount == 0 ? " WHERE " : " AND ");
            sqlStrings.add("\"created_by\" = '" + userId + "'");
            filterCount++;
        }
        sqlStrings.add(";");
        return String.join("",sqlStrings);
    };

    private String toQuerySql(String collectionName, Map<String, List<String>> data, String readRule, String userId, boolean isAdmin){
        List<String> conditions = new ArrayList<>();
        List<String> orderBy = new ArrayList<>();
        String limit = null;
        String offset = null;

        for (var entry : data.entrySet()){
            var keyData = entry.getKey().split(":", 2);
            if (keyData.length > 1){
                switch (keyData[0]){
                    case "filter" -> {
                        var filterName = SqlUtils.quoteIdentifier(keyData[1]);
                        for (var value : entry.getValue()){
                            conditions.add(filterName + " " + SqlUtils.validateFilter(value));
                        }
                    }
                    case "sort" -> {
                        orderBy.add(SqlUtils.quoteIdentifier(keyData[1]) + " " + entry.getValue().getFirst());
                    }
                }
            } else {
                switch (keyData[0]){
                    case "limit" -> limit = entry.getValue().getFirst();
                    case "offset" -> offset = entry.getValue().getFirst();
                }
            }
        }

        if (!isAdmin && "USER".equals(readRule) && userId != null) {
            conditions.add("\"created_by\" = '" + userId + "'");
        }

        String sql = "SELECT * FROM " + SqlUtils.quoteIdentifier(collectionName);
        if (!conditions.isEmpty()) sql += " WHERE " + String.join(" AND ", conditions);
        if (!orderBy.isEmpty()) sql += " ORDER BY " + String.join(", ", orderBy);
        if (limit != null) sql += " LIMIT " + SqlUtils.parseIntParam(limit);
        if (offset != null) sql += " OFFSET " + SqlUtils.parseIntParam(offset);
        sql += ";";
        return sql;
    }

    private String toSqlString(FieldDto field){
        String name = SqlUtils.quoteIdentifier(field.name());
        return switch (field.type()) {
            case DataTypeEnum.BOOLEAN -> name + " INTEGER DEFAULT 0 " + (field.required() ? "NOT NULL":"");
            case DataTypeEnum.NUMBER -> name + " REAL DEFAULT 0.0 " + (field.required() ? "NOT NULL":"");
            case DataTypeEnum.TEXT -> name + " TEXT DEFAULT '' " + (field.required() ? "NOT NULL":"");
            case DataTypeEnum.JSON -> name + " TEXT DEFAULT '{}' " + (field.required() ? "NOT NULL":"");
            case DataTypeEnum.URL -> name + " TEXT DEFAULT '' " + (field.required() ? "NOT NULL":"");
            case DataTypeEnum.EMAIL -> name + " TEXT DEFAULT '' " + (field.required() ? "NOT NULL":"");
            case DataTypeEnum.DATETIME -> name + " TEXT DEFAULT CURRENT_TIMESTAMP " + (field.required() ? "NOT NULL":"");
        };
    }

//    public void updateNewCollection(CollectionDto incommingCollection){
//        CollectionDto currSchema = this.jdbi.withHandle(handle -> handle.createQuery("SELECT * FROM collections WHERE name = :name")
//                .bind("name", incommingCollection.name())
//                .map((rs, ctx) -> {
//                    try {
//                        return new CollectionDto(
//                                rs.getString("name"),
//                                new ObjectMapper().readValue(rs.getString("json_schema"), new TypeReference<List<FieldDto>>() {}));
//                    } catch (JsonProcessingException e) {
//                        throw new RuntimeException(e);
//                    }
//                }).one()
//        );
//
//        if (incommingCollection.fields().size() > currSchema.fields().size()){
//
//
//        }
//
//    }
}
