package com.snapbase.models;

public class CollectionModel {
    private Integer id;
    private String name;
    private String json_schema;
    private String read_rule;
    private String update_rule;

    public Integer getId() { return id; }
    public String getName() { return name; }
    public String getJson_schema() { return json_schema; }
    public String getRead_rule() { return read_rule; }
    public String getUpdate_rule() { return update_rule; }

    public void setId(Integer id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setJson_schema(String json_schema) { this.json_schema = json_schema; }
    public void setRead_rule(String read_rule) { this.read_rule = read_rule; }
    public void setUpdate_rule(String update_rule) { this.update_rule = update_rule; }
}
