package com.snapbase.repositories;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snapbase.dtos.*;
import com.snapbase.enums.DataTypeEnum;
import com.snapbase.models.CollectionModel;
import org.jdbi.v3.core.Jdbi;
import com.snapbase.utils.SqlUtils;

import java.util.*;
import java.util.stream.Collectors;

public class CollectionRepository {
    private final Jdbi jdbi;

    public CollectionRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    private record SqlParts(String sql, Map<String, Object> params) {}

    // ═══ Schema management ═══

    public boolean collectionExists(String name) {
        List<CollectionModel> results = jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM collections WHERE name = :name")
                        .bind("name", name)
                        .mapToBean(CollectionModel.class)
                        .list());
        return !results.isEmpty();
    }

    public void createTable(CreateCollectionDTO collection) {
        String tableName = SqlUtils.quoteIdentifier(collection.name());
        String columns = collection.fields().stream()
                .map(this::buildColumnDef)
                .collect(Collectors.joining(", "));
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName
                + " ( id TEXT PRIMARY KEY, " + columns
                + ", created_at TEXT DEFAULT CURRENT_TIMESTAMP, created_by TEXT DEFAULT '' );";
        jdbi.withHandle(handle -> handle.execute(sql));
    }

    public Integer saveCollection(CreateCollectionDTO collection) {
        return jdbi.withHandle(handle -> {
            try {
                return handle.createUpdate(
                        "INSERT INTO collections (name, json_schema, read_rule, update_rule) VALUES (:name, :jsonSchema, :readRule, :updateRule)"
                )
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

    public List<CollectionModel> findAllCollections() {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM collections ORDER BY rowid ASC")
                        .mapToBean(CollectionModel.class)
                        .list()
        );
    }

    public Optional<CollectionModel> findCollectionSchema(String name) {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM collections WHERE name = :name")
                        .bind("name", name)
                        .mapToBean(CollectionModel.class)
                        .findOne()
        );
    }

    // ═══ Record operations ═══

    public String insertRecord(InsertRecordDTO dto, String userId) {
        var allColumns = new LinkedHashSet<String>();
        allColumns.add("id");
        allColumns.add("created_by");
        allColumns.addAll(dto.data().keySet());

        String columns = allColumns.stream()
                .map(SqlUtils::quoteIdentifier)
                .collect(Collectors.joining(", "));

        String placeholders = allColumns.stream()
                .map(k -> ":" + SqlUtils.validateIdentifier(k))
                .collect(Collectors.joining(", "));

        String table = SqlUtils.quoteIdentifier(dto.name());
        String sql = "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")";

        String id = SqlUtils.generateId();
        Map<String, Object> params = new LinkedHashMap<>();
        params.putAll(dto.data());
        params.put("id", id);
        params.put("created_by", userId != null ? userId : "");

        jdbi.withHandle(handle ->
                handle.createUpdate(sql)
                        .bindMap(params)
                        .execute()
        );
        return id;
    }

    public List<Map<String, Object>> findRecords(String collectionName, Map<String, List<String>> queryParams,
                                                  String readRule, String userId, boolean isAdmin) {
        if (!isAdmin && "ADMIN".equals(readRule)) {
            return List.of();
        }
        SqlParts parts = buildSelectSql(collectionName, queryParams, readRule, userId, isAdmin);
        return jdbi.withHandle(handle ->
                handle.createQuery(parts.sql())
                        .bindMap(parts.params())
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

    public int updateRecords(UpdateRecordDTO dto, String updateRule, String userId, boolean isAdmin) {
        if (!isAdmin && "ADMIN".equals(updateRule)) {
            return 0;
        }
        SqlParts parts = buildUpdateSql(dto.name(), dto.data(), dto.filter(), updateRule, userId, isAdmin);
        return jdbi.withHandle(handle ->
                handle.createUpdate(parts.sql())
                        .bindMap(parts.params())
                        .execute()
        );
    }

    public int deleteRecords(String collectionName, Map<String, List<String>> queryParams,
                              String updateRule, String userId, boolean isAdmin) {
        if (!isAdmin && "ADMIN".equals(updateRule)) {
            return 0;
        }
        SqlParts parts = buildDeleteSql(collectionName, queryParams, updateRule, userId, isAdmin);
        return jdbi.withHandle(handle ->
                handle.createUpdate(parts.sql())
                        .bindMap(parts.params())
                        .execute()
        );
    }

    // ═══ SQL builders ═══

    private SqlParts buildSelectSql(String collectionName, Map<String, List<String>> data,
                                     String readRule, String userId, boolean isAdmin) {
        List<String> conditions = new ArrayList<>();
        List<String> orderBy = new ArrayList<>();
        String limit = null;
        String offset = null;
        Map<String, Object> params = new LinkedHashMap<>();
        int paramIdx = 0;

        for (var entry : data.entrySet()) {
            var keyData = entry.getKey().split(":", 2);
            if (keyData.length > 1) {
                switch (keyData[0]) {
                    case "filter" -> {
                        var filterName = SqlUtils.quoteIdentifier(keyData[1]);
                        for (var value : entry.getValue()) {
                            var fp = SqlUtils.parseFilter(value);
                            String paramName = "f" + paramIdx;
                            conditions.add(filterName + " " + fp.operator() + " :" + paramName);
                            params.put(paramName, fp.value());
                            paramIdx++;
                        }
                    }
                    case "sort" -> {
                        String dir = entry.getValue().getFirst().toUpperCase();
                        if (!dir.equals("ASC") && !dir.equals("DESC")) dir = "ASC";
                        orderBy.add(SqlUtils.quoteIdentifier(keyData[1]) + " " + dir);
                    }
                }
            } else {
                switch (keyData[0]) {
                    case "limit" -> limit = entry.getValue().getFirst();
                    case "offset" -> offset = entry.getValue().getFirst();
                }
            }
        }

        if (!isAdmin && "USER".equals(readRule) && userId != null) {
            conditions.add("\"created_by\" = :_cb");
            params.put("_cb", userId);
        }

        String sql = "SELECT * FROM " + SqlUtils.quoteIdentifier(collectionName);
        if (!conditions.isEmpty()) sql += " WHERE " + String.join(" AND ", conditions);
        if (!orderBy.isEmpty()) sql += " ORDER BY " + String.join(", ", orderBy);
        if (limit != null) sql += " LIMIT " + SqlUtils.parseIntParam(limit);
        if (offset != null) sql += " OFFSET " + SqlUtils.parseIntParam(offset);
        sql += ";";
        return new SqlParts(sql, params);
    }

    private SqlParts buildUpdateSql(String collectionName, Map<String, Object> data, Map<String, String> filter,
                                     String updateRule, String userId, boolean isAdmin) {
        List<String> sqlStrings = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        int i = 0;
        int dataI = 0;
        int paramIdx = 0;
        sqlStrings.add("UPDATE " + SqlUtils.quoteIdentifier(collectionName));
        for (var dataEntry : data.entrySet()) {
            String paramName = "s" + paramIdx;
            if (dataI < 1) {
                sqlStrings.add(" SET " + SqlUtils.quoteIdentifier(dataEntry.getKey()) + " = :" + paramName);
                dataI++;
            } else {
                sqlStrings.add(", " + SqlUtils.quoteIdentifier(dataEntry.getKey()) + " = :" + paramName);
                dataI++;
            }
            params.put(paramName, dataEntry.getValue());
            paramIdx++;
        }
        for (var filterEntry : filter.entrySet()) {
            var fp = SqlUtils.parseFilter(filterEntry.getValue());
            String paramName = "f" + paramIdx;
            if (i < 1) {
                sqlStrings.add(" WHERE " + SqlUtils.quoteIdentifier(filterEntry.getKey()) + " " + fp.operator() + " :" + paramName);
                i++;
            } else {
                sqlStrings.add(" AND " + SqlUtils.quoteIdentifier(filterEntry.getKey()) + " " + fp.operator() + " :" + paramName);
                i++;
            }
            params.put(paramName, fp.value());
            paramIdx++;
        }
        if (!isAdmin && "USER".equals(updateRule) && userId != null) {
            sqlStrings.add(i == 0 ? " WHERE " : " AND ");
            sqlStrings.add("\"created_by\" = :_cb");
            params.put("_cb", userId);
        }
        sqlStrings.add(";");
        return new SqlParts(String.join("", sqlStrings), params);
    }

    private SqlParts buildDeleteSql(String collectionName, Map<String, List<String>> data,
                                     String updateRule, String userId, boolean isAdmin) {
        var filterCount = 0;
        List<String> sqlStrings = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        int paramIdx = 0;
        sqlStrings.add("DELETE FROM " + SqlUtils.quoteIdentifier(collectionName));
        for (var entry : data.entrySet()) {
            var keyData = entry.getKey().split(":", 2);
            if (keyData.length < 2 || !"filter".equals(keyData[0])) continue;
            var filterName = SqlUtils.quoteIdentifier(keyData[1]);
            var filterData = entry.getValue();
            for (var filterValue : filterData) {
                var fp = SqlUtils.parseFilter(filterValue);
                String paramName = "f" + paramIdx;
                if (filterCount == 0) {
                    sqlStrings.add(" WHERE ");
                    sqlStrings.add(filterName + " " + fp.operator() + " :" + paramName);
                    filterCount++;
                } else {
                    sqlStrings.add(" AND ");
                    sqlStrings.add(filterName + " " + fp.operator() + " :" + paramName);
                    filterCount++;
                }
                params.put(paramName, fp.value());
                paramIdx++;
            }
        }
        if (!isAdmin && "USER".equals(updateRule) && userId != null) {
            sqlStrings.add(filterCount == 0 ? " WHERE " : " AND ");
            sqlStrings.add("\"created_by\" = :_cb");
            params.put("_cb", userId);
            filterCount++;
        }
        sqlStrings.add(";");
        return new SqlParts(String.join("", sqlStrings), params);
    }

    private String buildColumnDef(FieldDefinition field) {
        String name = SqlUtils.quoteIdentifier(field.name());
        return switch (field.type()) {
            case DataTypeEnum.BOOLEAN -> name + " INTEGER DEFAULT 0 " + (field.required() ? "NOT NULL" : "");
            case DataTypeEnum.NUMBER -> name + " REAL DEFAULT 0.0 " + (field.required() ? "NOT NULL" : "");
            case DataTypeEnum.TEXT -> name + " TEXT DEFAULT '' " + (field.required() ? "NOT NULL" : "");
            case DataTypeEnum.JSON -> name + " TEXT DEFAULT '{}' " + (field.required() ? "NOT NULL" : "");
            case DataTypeEnum.URL -> name + " TEXT DEFAULT '' " + (field.required() ? "NOT NULL" : "");
            case DataTypeEnum.EMAIL -> name + " TEXT DEFAULT '' " + (field.required() ? "NOT NULL" : "");
            case DataTypeEnum.DATETIME ->
                    name + " TEXT DEFAULT CURRENT_TIMESTAMP " + (field.required() ? "NOT NULL" : "");
        };
    }

    // ═══ Future ═══

//    public void updateNewCollection(CreateCollectionDTO incommingCollection){
//        CreateCollectionDTO currSchema = this.jdbi.withHandle(handle -> handle.createQuery("SELECT * FROM collections WHERE name = :name")
//                .bind("name", incommingCollection.name())
//                .map((rs, ctx) -> {
//                    try {
//                        return new CreateCollectionDTO(
//                                rs.getString("name"),
//                                new ObjectMapper().readValue(rs.getString("json_schema"), new TypeReference<List<FieldDefinition>>() {}),
//                                rs.getString("read_rule"),
//                                rs.getString("update_rule"));
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
